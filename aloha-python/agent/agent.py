"""Dice Agent with REST transport support."""

import asyncio
import logging
import os
from pathlib import Path
from typing import Dict

from dotenv import load_dotenv
from a2a.server.apps.rest import A2ARESTFastAPIApplication
from a2a.server.apps.jsonrpc import JSONRPCApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore
from a2a.types import AgentCard, AgentCapabilities, AgentSkill, AgentProvider
import uvicorn

from agent_executor import DiceAgentExecutor

# Load environment variables from .env file
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class DiceAgent:
    """
    Dice Agent implementing A2A protocol with REST transport support.
    """
    
    def __init__(
        self,
        grpc_port: int = 13000,
        jsonrpc_port: int = 13001,
        rest_port: int = 13002,
        host: str = "0.0.0.0"
    ):
        """
        Initialize the Dice Agent.
        
        Args:
            grpc_port: Port for gRPC transport
            jsonrpc_port: Port for JSON-RPC 2.0 transport
            rest_port: Port for REST transport
            host: Host address to bind to
        """
        self.grpc_port = grpc_port
        self.jsonrpc_port = jsonrpc_port
        self.rest_port = rest_port
        self.host = host
        
        # Create agent card
        self.agent_card = self._create_agent_card()
        
        # Create agent executor
        self.executor = DiceAgentExecutor()
        
        # Initialize transports
        self.transports = []
        self.servers = []
        
        logger.info("DiceAgent initialized")
    
    def _create_agent_card(self) -> AgentCard:
        """
        Create the agent card describing capabilities.
        
        Returns:
            AgentCard with agent metadata
        """
        return AgentCard(
            protocol_version="0.3.0",
            name=os.getenv("AGENT_NAME", "Dice Agent"),
            description=os.getenv("AGENT_DESCRIPTION", "An agent that can roll arbitrary dice and check prime numbers"),
            url=f"http://localhost:{self.rest_port}",
            provider=AgentProvider(
                organization="Aloha A2A",
                url="https://github.com/google/aloha-a2a"
            ),
            version=os.getenv("AGENT_VERSION", "1.0.0"),
            capabilities=AgentCapabilities(
                streaming=True,
                push_notifications=False,
                state_transition_history=True
            ),
            default_input_modes=["text"],
            default_output_modes=["text", "task-status"],
            skills=[
                AgentSkill(
                    id="roll-dice",
                    name="Roll Dice",
                    description="Rolls an N-sided dice",
                    tags=["dice", "random"],
                    examples=["Roll a 20-sided dice", "Roll a 6-sided dice"],
                    input_modes=["text"],
                    output_modes=["text", "task-status"]
                ),
                AgentSkill(
                    id="check-prime",
                    name="Prime Checker",
                    description="Checks if numbers are prime",
                    tags=["math", "prime"],
                    examples=["Is 17 prime?", "Check if 2, 4, 7, 9, 11 are prime"],
                    input_modes=["text"],
                    output_modes=["text", "task-status"]
                )
            ],
            supports_authenticated_extended_card=False,
            preferred_transport="REST"
        )
    
    async def start(self):
        """Start all transport servers."""
        logger.info("Starting Dice Agent with REST transport support")
        
        # Create task store
        task_store = InMemoryTaskStore()
        
        # Create request handler
        request_handler = DefaultRequestHandler(
            agent_executor=self.executor,
            task_store=task_store
        )
        
        # Setup REST transport (primary transport)
        try:
            rest_app = A2ARESTFastAPIApplication(
                agent_card=self.agent_card,
                http_handler=request_handler
            )
            app = rest_app.build()

            @app.get("/v1/transports")
            async def get_transport_capabilities():
                return {
                    "rest": {
                        "implemented": True,
                        "stream": True,
                    },
                    "jsonrpc": {
                        "enabled": False,
                        "implemented": False,
                        "stream": False,
                    },
                    "grpc": {
                        "enabled": False,
                        "implemented": False,
                        "stream": False,
                    },
                    "experimentalTransports": False,
                }
            
            logger.info(f"REST transport configured on {self.host}:{self.rest_port}")
            
            # Start REST server
            config = uvicorn.Config(
                app,
                host=self.host,
                port=self.rest_port,
                log_level="info"
            )
            server = uvicorn.Server(config)
            self.servers.append(server)
            
            logger.info("=" * 60)
            logger.info("Dice Agent is running with the following transport:")
            logger.info(f"  - REST:         http://{self.host}:{self.rest_port}")
            logger.info(f"  - Agent Card:   http://{self.host}:{self.rest_port}/.well-known/agent-card.json")
            logger.info("=" * 60)
            
            await server.serve()
            
        except Exception as e:
            logger.error(f"Failed to setup REST transport on port {self.rest_port}: {e}", exc_info=True)
            raise
    
    async def stop(self):
        """Stop all transport servers."""
        logger.info("Stopping Dice Agent")
        
        for server in self.servers:
            server.should_exit = True
        
        logger.info("Dice Agent stopped")


async def main():
    """Main entry point for the agent."""
    # Get configuration from environment
    grpc_port = int(os.getenv("GRPC_PORT", "13000"))
    jsonrpc_port = int(os.getenv("JSONRPC_PORT", "13001"))
    rest_port = int(os.getenv("REST_PORT", "13002"))
    host = os.getenv("HOST", "0.0.0.0")
    
    # Create and start agent
    agent = DiceAgent(
        grpc_port=grpc_port,
        jsonrpc_port=jsonrpc_port,
        rest_port=rest_port,
        host=host
    )
    
    try:
        await agent.start()
        
        # Keep running until interrupted
        while True:
            await asyncio.sleep(1)
    except KeyboardInterrupt:
        logger.info("Received interrupt signal")
    finally:
        await agent.stop()


if __name__ == "__main__":
    asyncio.run(main())
