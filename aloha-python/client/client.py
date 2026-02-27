"""A2A client with multi-transport support (REST, JSON-RPC, gRPC)."""

import logging
from uuid import uuid4

import httpx
from a2a.client import A2ACardResolver
from a2a.client.transports import RestTransport
from a2a.client.transports import JsonRpcTransport
from a2a.types import (
    Message,
    MessageSendConfiguration,
    MessageSendParams,
    Task,
    TaskStatusUpdateEvent,
    TaskArtifactUpdateEvent,
    TextPart,
)

logger = logging.getLogger(__name__)


class AlohaClient:
    """
    A2A client with multi-transport support (REST, JSON-RPC, gRPC).
    """
    
    def __init__(self, server_url: str, transport: str = "rest"):
        """
        Create a new client.
        
        Args:
            server_url: The server URL to connect to
            transport: Transport protocol (rest, jsonrpc, grpc)
        """
        self.server_url = server_url
        self.transport = transport.lower()
        self.httpx_client = None
        self._transport = None
        self.agent_card = None
        self._grpc_channel = None
        
        logger.info(f"Client created for {server_url} using {self.transport} transport")
    
    async def initialize(self):
        """Initialize the client."""
        logger.info(f"Connecting to agent at: {self.server_url}")
        
        if self.transport == "grpc":
            await self._init_grpc()
        else:
            await self._init_http()
        
        logger.info("Client initialized successfully")

    async def _init_http(self):
        """Initialize HTTP-based transport (REST or JSON-RPC)."""
        self.httpx_client = httpx.AsyncClient(timeout=30)
        
        card_resolver = A2ACardResolver(self.httpx_client, self.server_url)
        self.agent_card = await card_resolver.get_agent_card()
        
        logger.info(f"Agent card retrieved: {self.agent_card.name}")
        logger.info(f"Streaming supported: {self.agent_card.capabilities.streaming}")
        
        if self.transport == "jsonrpc":
            self._transport = JsonRpcTransport(
                httpx_client=self.httpx_client,
                agent_card=self.agent_card
            )
            logger.info("Using JSON-RPC transport")
        else:
            self._transport = RestTransport(
                httpx_client=self.httpx_client,
                agent_card=self.agent_card
            )
            logger.info("Using REST transport")

    async def _init_grpc(self):
        """Initialize gRPC transport."""
        import grpc.aio
        from a2a.client.transports import GrpcTransport
        
        # For agent card, we need HTTP - use the REST port (server_url is host:port for gRPC)
        # Try to fetch agent card via HTTP on rest port
        host_port = self.server_url.replace("http://", "").replace("https://", "")
        parts = host_port.split(":")
        host = parts[0]
        grpc_port = int(parts[1]) if len(parts) > 1 else 13000
        # Agent card is served on REST port (grpc_port + 2 by convention)
        rest_port = grpc_port + 2
        agent_card_url = f"http://{host}:{rest_port}"
        
        self.httpx_client = httpx.AsyncClient(timeout=30)
        card_resolver = A2ACardResolver(self.httpx_client, agent_card_url)
        self.agent_card = await card_resolver.get_agent_card()
        
        logger.info(f"Agent card retrieved: {self.agent_card.name}")
        logger.info(f"Streaming supported: {self.agent_card.capabilities.streaming}")
        
        # Create gRPC channel
        self._grpc_channel = grpc.aio.insecure_channel(self.server_url)
        self._transport = GrpcTransport(
            channel=self._grpc_channel,
            agent_card=self.agent_card
        )
        logger.info("Using gRPC transport")
    
    async def send_message(self, message_text: str) -> str:
        """
        Send a message to the agent and wait for response.
        
        Args:
            message_text: The message text to send
            
        Returns:
            The agent's response
        """
        if not self._transport:
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
            message=message,
            configuration=MessageSendConfiguration(
                accepted_output_modes=['text'],
            ),
        )
        
        # Send message and collect response
        response_parts = []
        
        if self.agent_card.capabilities.streaming:
            # Use streaming
            async for event in self._transport.send_message_streaming(payload):
                if isinstance(event, TaskStatusUpdateEvent):
                    logger.info(f"Task status: {event.status.state}")
                    if event.status.message:
                        for part in event.status.message.parts:
                            if hasattr(part.root, 'text'):
                                response_parts.append(part.root.text)
                elif isinstance(event, TaskArtifactUpdateEvent):
                    logger.info(f"Artifact update received")
                elif isinstance(event, Task):
                    if event.status and event.status.message:
                        for part in event.status.message.parts:
                            if hasattr(part.root, 'text'):
                                response_parts.append(part.root.text)
                elif isinstance(event, Message):
                    for part in event.parts:
                        if hasattr(part.root, 'text'):
                            response_parts.append(part.root.text)
        else:
            # Non-streaming
            result = await self._transport.send_message(payload)
            
            if isinstance(result, Task):
                if result.status and result.status.message:
                    for part in result.status.message.parts:
                        if hasattr(part.root, 'text'):
                            response_parts.append(part.root.text)
            elif isinstance(result, Message):
                for part in result.parts:
                    if hasattr(part.root, 'text'):
                        response_parts.append(part.root.text)
        
        # Return combined response
        final_text = "".join(response_parts)
        logger.info(f"Final response length: {len(final_text)}")
        return final_text
    
    async def close(self):
        """Clean up client resources."""
        logger.info("Cleaning up resources...")
        
        if self._transport:
            await self._transport.close()
        elif self.httpx_client:
            await self.httpx_client.aclose()
        
        if self._grpc_channel:
            await self._grpc_channel.close()
        
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
