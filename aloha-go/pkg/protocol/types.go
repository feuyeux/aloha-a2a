package protocol

import (
	"time"

	"github.com/google/uuid"
)

// Task state constants
const (
	TaskStateSubmitted = "submitted"
	TaskStateWorking   = "working"
	TaskStateCompleted = "completed"
	TaskStateFailed    = "failed"
	TaskStateCanceled  = "canceled"
)

// NewUUID generates a new UUID string
func NewUUID() string {
	return uuid.New().String()
}

// Now returns the current time in ISO8601 format
func Now() string {
	return time.Now().UTC().Format(time.RFC3339)
}

// Message represents an A2A message
type Message struct {
	Kind      string `json:"kind"`
	MessageID string `json:"messageId"`
	Role      string `json:"role"`
	Parts     []Part `json:"parts"`
	ContextID string `json:"contextId,omitempty"`
	TaskID    string `json:"taskId,omitempty"`
}

// Part represents a message part
type Part struct {
	Kind string `json:"kind"`
	Text string `json:"text,omitempty"`
}

// Task represents an A2A task
type Task struct {
	Kind      string     `json:"kind"`
	ID        string     `json:"id"`
	ContextID string     `json:"contextId"`
	Status    TaskStatus `json:"status"`
	History   []Message  `json:"history,omitempty"`
}

// TaskStatus represents the status of a task
type TaskStatus struct {
	State     string   `json:"state"`
	Timestamp string   `json:"timestamp"`
	Message   *Message `json:"message,omitempty"`
}

// Event represents an A2A event
type Event struct {
	Kind      string      `json:"kind"`
	TaskID    string      `json:"taskId,omitempty"`
	ContextID string      `json:"contextId,omitempty"`
	Status    *TaskStatus `json:"status,omitempty"`
	Final     bool        `json:"final,omitempty"`
}

// TaskStatusUpdateEvent represents a task status update event
type TaskStatusUpdateEvent struct {
	Kind      string     `json:"kind"`
	TaskID    string     `json:"taskId"`
	ContextID string     `json:"contextId"`
	Status    TaskStatus `json:"status"`
	Final     bool       `json:"final"`
}

// AgentCard represents an agent's capabilities
type AgentCard struct {
	Name               string       `json:"name"`
	Description        string       `json:"description"`
	URL                string       `json:"url"`
	Version            string       `json:"version"`
	Capabilities       Capability   `json:"capabilities"`
	DefaultInputModes  []string     `json:"defaultInputModes"`
	DefaultOutputModes []string     `json:"defaultOutputModes"`
	Skills             []Skill      `json:"skills"`
	PreferredTransport string       `json:"preferredTransport"`
}

// Capability represents agent capabilities
type Capability struct {
	Streaming         bool `json:"streaming"`
	PushNotifications bool `json:"pushNotifications"`
}

// Skill represents an agent skill
type Skill struct {
	ID          string   `json:"id"`
	Name        string   `json:"name"`
	Description string   `json:"description"`
	Tags        []string `json:"tags"`
	Examples    []string `json:"examples"`
}

