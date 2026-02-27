package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/a2aproject/a2a-go/a2a"
	"github.com/a2aproject/a2a-go/a2aclient/agentcard"
)

// RESTClient implements a custom REST transport for A2A
type RESTClient struct {
	serverURL  string
	httpClient *http.Client
	agentCard  *a2a.AgentCard
}

// NewRESTClient creates a new REST client
func NewRESTClient(ctx context.Context, serverURL, cardURL string) (*RESTClient, error) {
	client := &RESTClient{
		serverURL:  serverURL,
		httpClient: &http.Client{Timeout: 120 * time.Second},
	}

	// Resolve agent card
	if cardURL == "" {
		cardURL = serverURL
	}
	card, err := agentcard.DefaultResolver.Resolve(ctx, cardURL)
	if err != nil {
		return nil, fmt.Errorf("failed to resolve agent card: %w", err)
	}
	client.agentCard = card

	return client, nil
}

// GetAgentCard returns the agent card
func (c *RESTClient) GetAgentCard() *a2a.AgentCard {
	return c.agentCard
}

// Destroy cleans up the client
func (c *RESTClient) Destroy() {
	// Nothing to clean up for HTTP client
}

// SendMessage sends a non-streaming message
func (c *RESTClient) SendMessage(ctx context.Context, params *a2a.MessageSendParams) (*a2a.Task, error) {
	// Build REST request - extract message from params
	type MessageSendRequest struct {
		Message *a2a.Message `json:"message"`
	}

	reqBody := MessageSendRequest{
		Message: params.Message,
	}

	jsonBody, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	url := c.serverURL + "/v1/message:send"
	log.Printf("Sending POST request to: %s", url)

	req, err := http.NewRequestWithContext(ctx, "POST", url, strings.NewReader(string(jsonBody)))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("server returned status %d: %s", resp.StatusCode, string(body))
	}

	var task a2a.Task
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &task, nil
}

// SendStreamingMessage sends a streaming message
func (c *RESTClient) SendStreamingMessage(ctx context.Context, params *a2a.MessageSendParams) <-chan interface{} {
	resultChan := make(chan interface{}, 10)

	go func() {
		defer close(resultChan)

		type MessageSendRequest struct {
			Message *a2a.Message `json:"message"`
		}

		reqBody := MessageSendRequest{
			Message: params.Message,
		}

		jsonBody, _ := json.Marshal(reqBody)
		url := c.serverURL + "/v1/message:stream"

		req, err := http.NewRequestWithContext(ctx, "POST", url, strings.NewReader(string(jsonBody)))
		if err != nil {
			resultChan <- fmt.Errorf("failed to create request: %w", err)
			return
		}
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Accept", "text/event-stream")

		resp, err := c.httpClient.Do(req)
		if err != nil {
			resultChan <- fmt.Errorf("request failed: %w", err)
			return
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			body, _ := io.ReadAll(resp.Body)
			resultChan <- fmt.Errorf("server returned status %d: %s", resp.StatusCode, string(body))
			return
		}

		reader := bufio.NewReader(resp.Body)
		for {
			line, err := reader.ReadString('\n')
			if err != nil {
				break
			}

			line = strings.TrimSpace(line)
			if strings.HasPrefix(line, "data: ") {
				data := strings.TrimPrefix(line, "data: ")
				if data == "[DONE]" {
					break
				}

				// Try to parse as TaskStatusUpdateEvent
				var event map[string]interface{}
				if err := json.Unmarshal([]byte(data), &event); err != nil {
					continue
				}

				// Check event type
				if taskStatus, ok := event["taskStatus"]; ok {
					taskStatusMap := taskStatus.(map[string]interface{})
					state := taskStatusMap["state"].(string)

					var msg *a2a.Message
					if msgData, ok := taskStatusMap["message"]; ok && msgData != nil {
						msgDataMap := msgData.(map[string]interface{})
						roleStr := msgDataMap["role"].(string)
						role := a2a.MessageRoleUser
						if roleStr == "agent" {
							role = a2a.MessageRoleAgent
						}
						msg = &a2a.Message{Role: role}
						if parts, ok := msgDataMap["parts"].([]interface{}); ok {
							for _, p := range parts {
								partMap := p.(map[string]interface{})
								if textPart, ok := partMap["text"]; ok {
									msg.Parts = append(msg.Parts, a2a.TextPart{Text: textPart.(string)})
								}
							}
						}
					}

					updater := &a2a.TaskStatusUpdateEvent{
						Status: a2a.TaskStatus{
							State:   a2a.TaskState(state),
							Message: msg,
						},
					}
					resultChan <- updater
				}
			}
		}
	}()

	return resultChan
}

// GetTask gets a task by ID
func (c *RESTClient) GetTask(ctx context.Context, taskID string) (*a2a.Task, error) {
	url := fmt.Sprintf("%s/v1/tasks/%s", c.serverURL, taskID)

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("server returned status %d: %s", resp.StatusCode, string(body))
	}

	var task a2a.Task
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &task, nil
}

// CancelTask cancels a task
func (c *RESTClient) CancelTask(ctx context.Context, taskID string) (*a2a.Task, error) {
	url := fmt.Sprintf("%s/v1/tasks/%s:cancel", c.serverURL, taskID)

	req, err := http.NewRequestWithContext(ctx, "POST", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("server returned status %d: %s", resp.StatusCode, string(body))
	}

	var task a2a.Task
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &task, nil
}
