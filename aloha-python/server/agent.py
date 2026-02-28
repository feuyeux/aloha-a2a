"""Dice Agent with multi-transport support (REST, JSON-RPC, gRPC)."""

import asyncio
import logging
import os
import sys
from pathlib import Path
from typing import Dict

from dotenv import load_dotenv
from a2a.server.apps.rest import A2ARESTFastAPIApplication
from a2a.server.apps.jsonrpc import A2AFastAPIApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore
from a2a.types import AgentCard, AgentCapabilities, AgentSkill, AgentProvider
import uvicorn

from .agent_executor import DiceAgentExecutor

# Load environment variables from .env file
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


class DiceAgent:
    """
    Dice Agent implementing A2A protocol with REST, JSON-RPC, and gRPC transport support.
    """

    def __init__(
        self,
        grpc_port: int = 13000,
        jsonrpc_port: int = 13001,
        rest_port: int = 13002,
        host: str = "0.0.0.0",
        transport_mode: str = "rest",
    ):
        """
        Initialize the Dice Agent.

        Args:
            grpc_port: Port for gRPC transport
            jsonrpc_port: Port for JSON-RPC 2.0 transport
            rest_port: Port for REST transport
            host: Host address to bind to
            transport_mode: Transport mode (rest, jsonrpc, grpc)
        """
        self.grpc_port = grpc_port
        self.jsonrpc_port = jsonrpc_port
        self.rest_port = rest_port
        self.host = host
        self.transport_mode = transport_mode.lower()

        # Create agent card
        self.agent_card = self._create_agent_card()

        # Create agent executor
        self.executor = DiceAgentExecutor()

        # Initialize transports
        self.transports = []
        self.servers = []

        logger.info("Dice Agent initialized")

    def _create_agent_card(self) -> AgentCard:
        """
        Create the agent card describing capabilities.

        Returns:
            AgentCard with agent metadata
        """
        # Set URL and preferred transport based on mode
        if self.transport_mode == "grpc":
            url = f"localhost:{self.grpc_port}"
            preferred = "GRPC"
        elif self.transport_mode == "jsonrpc":
            url = f"http://localhost:{self.jsonrpc_port}"
            preferred = "JSONRPC"
        else:
            url = f"http://localhost:{self.rest_port}"
            preferred = "HTTP+JSON"

        return AgentCard(
            protocol_version="0.3.0",
            name=os.getenv("AGENT_NAME", "Dice Agent"),
            description=os.getenv(
                "AGENT_DESCRIPTION", "An agent that can roll arbitrary dice and check prime numbers"
            ),
            url=url,
            provider=AgentProvider(
                organization="Aloha A2A", url="https://github.com/google/aloha-a2a"
            ),
            version=os.getenv("AGENT_VERSION", "1.0.0"),
            capabilities=AgentCapabilities(
                streaming=True, push_notifications=False, state_transition_history=True
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
                    output_modes=["text", "task-status"],
                ),
                AgentSkill(
                    id="check-prime",
                    name="Prime Checker",
                    description="Checks if numbers are prime",
                    tags=["math", "prime"],
                    examples=["Is 17 prime?", "Check if 2, 4, 7, 9, 11 are prime"],
                    input_modes=["text"],
                    output_modes=["text", "task-status"],
                ),
            ],
            supports_authenticated_extended_card=False,
            preferred_transport=preferred,
        )

    async def start(self):
        """Start transport server based on transport_mode."""
        logger.info("============================================================")
        logger.info("=== Dice Agent starting ===")
        logger.info("============================================================")

        # Create task store
        task_store = InMemoryTaskStore()

        # Create request handler
        request_handler = DefaultRequestHandler(agent_executor=self.executor, task_store=task_store)

        mode = self.transport_mode

        if mode == "jsonrpc":
            await self._start_jsonrpc(request_handler)
        elif mode == "grpc":
            await self._start_grpc(request_handler)
        else:
            await self._start_rest(request_handler)

    async def _start_rest(self, request_handler):
        """Start REST transport on rest_port."""
        try:
            rest_app = A2ARESTFastAPIApplication(
                agent_card=self.agent_card, http_handler=request_handler
            )
            app = rest_app.build()
            self._add_transport_endpoint(app, "rest")

            config = uvicorn.Config(app, host=self.host, port=self.rest_port, log_level="info")
            server = uvicorn.Server(config)
            self.servers.append(server)

            logger.info("============================================================")
            logger.info("Dice Agent is running:")
            logger.info("  - Transport:    REST")
            logger.info("  - REST:         http://%s:%s", self.host, self.rest_port)
            logger.info("============================================================")

            await server.serve()
        except Exception as e:
            logger.error(
                f"Failed to setup REST transport on port {self.rest_port}: {e}", exc_info=True
            )
            raise

    async def _start_jsonrpc(self, request_handler):
        """Start JSON-RPC transport on jsonrpc_port."""
        try:
            jsonrpc_app = A2AFastAPIApplication(
                agent_card=self.agent_card, http_handler=request_handler
            )
            app = jsonrpc_app.build()
            self._add_transport_endpoint(app, "jsonrpc")

            config = uvicorn.Config(app, host=self.host, port=self.jsonrpc_port, log_level="info")
            server = uvicorn.Server(config)
            self.servers.append(server)

            logger.info("============================================================")
            logger.info("Dice Agent is running:")
            logger.info("  - Transport:    JSON-RPC")
            logger.info("  - JSON-RPC:      http://%s:%s", self.host, self.jsonrpc_port)
            logger.info("============================================================")

            await server.serve()
        except Exception as e:
            logger.error(
                f"Failed to setup JSON-RPC transport on port {self.jsonrpc_port}: {e}",
                exc_info=True,
            )
            raise

    async def _start_grpc(self, request_handler):
        """Start gRPC transport on grpc_port, with REST on rest_port for agent card."""
        try:
            import grpc.aio
            from a2a.server.request_handlers.grpc_handler import GrpcHandler
            from a2a.grpc import a2a_pb2_grpc

            # Create and start gRPC server
            grpc_server = grpc.aio.server()
            handler = GrpcHandler(agent_card=self.agent_card, request_handler=request_handler)
            a2a_pb2_grpc.add_A2AServiceServicer_to_server(handler, grpc_server)
            grpc_server.add_insecure_port(f"{self.host}:{self.grpc_port}")
            await grpc_server.start()
            self._grpc_server = grpc_server

            # Also start a minimal REST server for agent card HTTP endpoint
            rest_app = A2ARESTFastAPIApplication(
                agent_card=self.agent_card, http_handler=request_handler
            )
            app = rest_app.build()
            self._add_transport_endpoint(app, "grpc")

            config = uvicorn.Config(app, host=self.host, port=self.rest_port, log_level="info")
            rest_server = uvicorn.Server(config)
            self.servers.append(rest_server)

            logger.info("============================================================")
            logger.info("Dice Agent is running:")
            logger.info("  - Transport:    gRPC")
            logger.info("  - gRPC:         %s:%s", self.host, self.grpc_port)
            logger.info("============================================================")

            # Run REST (for agent card) alongside gRPC
            await asyncio.gather(rest_server.serve(), grpc_server.wait_for_termination())
        except Exception as e:
            logger.error(
                f"Failed to setup gRPC transport on port {self.grpc_port}: {e}", exc_info=True
            )
            raise

    def _add_transport_endpoint(self, app, active_mode: str):
        """Add /v1/transports endpoint to a FastAPI app."""
        from fastapi import FastAPI

        @app.get("/v1/transports")
        async def get_transport_capabilities():
            return {
                "rest": {"implemented": True, "stream": True},
                "jsonrpc": {"implemented": True, "stream": True},
                "grpc": {"implemented": True, "stream": True},
                "activeTransport": active_mode,
            }

    async def stop(self):
        """Stop all transport servers."""
        logger.info("Shutdown signal received, stopping Dice Agent...")

        for server in self.servers:
            server.should_exit = True

        if hasattr(self, "_grpc_server") and self._grpc_server:
            await self._grpc_server.stop(grace=5)

        logger.info("Dice Agent stopped")


async def main():
    """Main entry point for the agent."""
    # Get configuration from environment
    grpc_port = int(os.getenv("GRPC_PORT", "13000"))
    jsonrpc_port = int(os.getenv("JSONRPC_PORT", "13001"))
    rest_port = int(os.getenv("REST_PORT", "13002"))
    host = os.getenv("HOST", "0.0.0.0")
    transport_mode = os.getenv("TRANSPORT_MODE", "rest").lower()

    logger.info(f"Server transport: {transport_mode.upper()}")

    # Create and start agent
    agent = DiceAgent(
        grpc_port=grpc_port,
        jsonrpc_port=jsonrpc_port,
        rest_port=rest_port,
        host=host,
        transport_mode=transport_mode,
    )

    try:
        await agent.start()

        # Keep running until interrupted
        while True:
            await asyncio.sleep(1)
    except KeyboardInterrupt:
        logger.info("Shutdown signal received, stopping Dice Agent...")
    finally:
        await agent.stop()


if __name__ == "__main__":
    asyncio.run(main())
