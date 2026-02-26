"""A2A client with REST transport support."""

import logging
from uuid import uuid4

import httpx
from a2a.client import A2ACardResolver, A2AClient
from a2a.types import (
    Message,
    MessageSendConfiguration,
    MessageSendParams,
    SendMessageRequest,
    SendStreamingMessageRequest,
    Task,
    TaskStatusUpdateEvent,
    TaskArtifactUpdateEvent,
    TextPart,
    JSONRPCErrorResponse,
)

logger = logging.getLogger(__name__)


class AlohaClient:
    """
    A2A client with REST transport support.
    """
    
    def __init__(self, server_url: str, transport: str = "rest"):
        """
        Create a new client.
        
        Args:
            server_url: The server URL to connect to
            transport: Transport protocol to use (currently only rest is supported)
        """
        self.server_url = server_url
        self.transport = transport.lower()
        self.httpx_client = None
        self.client = None
        self.agent_card = None
        
        logger.info(f"Client created for {server_url} using {self.transport} transport")
    
    async def initialize(self):
        """Initialize the client."""
        logger.info(f"Connecting to agent at: {self.server_url}")
        
        # Create HTTP client
        self.httpx_client = httpx.AsyncClient(timeout=30)
        
        # Resolve agent card
        card_resolver = A2ACardResolver(self.httpx_client, self.server_url)
        self.agent_card = await card_resolver.get_agent_card()
        
        logger.info(f"Agent card retrieved: {self.agent_card.name}")
        logger.info(f"Streaming supported: {self.agent_card.capabilities.streaming}")
        
        # Create A2A client
        self.client = A2AClient(self.httpx_client, agent_card=self.agent_card)
        
        logger.info("Client initialized successfully")
    
    async def send_message(self, message_text: str) -> str:
        """
        Send a message to the agent and wait for response.
        
        Args:
            message_text: The message text to send
            
        Returns:
            The agent's response
        """
        if not self.client:
            raise RuntimeError("Client not initialized. Call initialize() first.")
        
        # Create the message
        message = Message(
            role='user',
            parts=[TextPart(text=message_text)],
            message_id=str(uuid4()),
        )
        
        logger.info(f"Sending message: {message_text}")
        
        # Prepare payload
        payload = MessageSendParams(
            id=str(uuid4()),
            message=message,
            configuration=MessageSendConfiguration(
                accepted_output_modes=['text'],
            ),
        )
        
        # Send message and collect response
        response_parts = []
        
        if self.agent_card.capabilities.streaming:
            # Use streaming
            response_stream = self.client.send_message_streaming(
                SendStreamingMessageRequest(
                    id=str(uuid4()),
                    params=payload,
                )
            )
            
            async for result in response_stream:
                if isinstance(result.root, JSONRPCErrorResponse):
                    error_msg = f"Error: {result.root.error}"
                    logger.error(error_msg)
                    return error_msg
                
                event = result.root.result
                
                if isinstance(event, TaskStatusUpdateEvent):
                    logger.info(f"Task status: {event.status.state}")
                    if event.status.message:
                        for part in event.status.message.parts:
                            if hasattr(part, 'text'):
                                response_parts.append(part.text)
                
                elif isinstance(event, Message):
                    for part in event.parts:
                        if hasattr(part, 'text'):
                            response_parts.append(part.text)
        else:
            # Non-streaming
            response = await self.client.send_message(
                SendMessageRequest(
                    id=str(uuid4()),
                    params=payload,
                )
            )
            
            if isinstance(response.root, JSONRPCErrorResponse):
                error_msg = f"Error: {response.root.error}"
                logger.error(error_msg)
                return error_msg
            
            event = response.root.result
            
            if isinstance(event, Task):
                if event.status.message:
                    for part in event.status.message.parts:
                        if hasattr(part, 'text'):
                            response_parts.append(part.text)
            elif isinstance(event, Message):
                for part in event.parts:
                    if hasattr(part, 'text'):
                        response_parts.append(part.text)
        
        # Return combined response
        final_text = "".join(response_parts)
        logger.info(f"Final response length: {len(final_text)}")
        return final_text
    
    async def close(self):
        """Clean up client resources."""
        logger.info("Cleaning up resources...")
        
        if self.httpx_client:
            await self.httpx_client.aclose()
        
        logger.info("Resource cleanup completed")

    async def probe_transports(self) -> dict:
        """
        Probe transport capabilities from the agent.

        Returns:
            Transport capability matrix from `/v1/transports`
        """
        if not self.httpx_client:
            raise RuntimeError("Client not initialized. Call initialize() first.")

        response = await self.httpx_client.get(f"{self.server_url}/v1/transports")
        response.raise_for_status()
        return response.json()
