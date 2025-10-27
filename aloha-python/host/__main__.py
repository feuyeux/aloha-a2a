"""Main entry point for the A2A Host client."""

import asyncio
import logging
import sys

import click

from client import Client

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def build_server_url(host: str, port: int, transport: str) -> str:
    """
    Build server URL based on transport protocol.
    
    Args:
        host: Server hostname
        port: Server port
        transport: Transport protocol
        
    Returns:
        Server URL
    """
    if transport == "grpc":
        return f"{host}:{port}"
    else:  # jsonrpc or rest
        return f"http://{host}:{port}"


@click.command()
@click.option(
    '--host',
    default='localhost',
    help='Agent hostname (default: localhost)'
)
@click.option(
    '--port',
    type=int,
    default=None,
    help='Agent port (default: 13000 for gRPC, 13001 for JSON-RPC, 13002 for REST)'
)
@click.option(
    '--transport',
    type=click.Choice(['jsonrpc', 'grpc', 'rest'], case_sensitive=False),
    default='grpc',
    help='Transport protocol (default: grpc)'
)
@click.option(
    '--message',
    default='Roll a 6-sided dice',
    help='Message to send (default: "Roll a 6-sided dice")'
)
def main(host: str, port: int, transport: str, message: str):
    """
    A2A Host client for sending messages to agents.
    
    Examples:
    
      # Send message using gRPC (default)
      python -m host --message "Roll a 20-sided dice"
    
      # Send message using JSON-RPC
      python -m host --transport jsonrpc --port 13001 --message "Is 17 prime?"
    
      # Send message using REST
      python -m host --transport rest --port 13002 --message "Check if 2, 7, 11 are prime"
    """
    # Set default port based on transport if not specified
    if port is None:
        if transport == 'grpc':
            port = 13000
        elif transport == 'jsonrpc':
            port = 13001
        elif transport == 'rest':
            port = 13002
    
    # Build server URL
    server_url = build_server_url(host, port, transport)
    
    logger.info("Configuration:")
    logger.info(f"  Host: {host}")
    logger.info(f"  Port: {port}")
    logger.info(f"  Transport: {transport}")
    logger.info(f"  Server URL: {server_url}")
    logger.info(f"  Message: {message}")
    
    # Run async client
    asyncio.run(run_client(server_url, transport, message))


async def run_client(server_url: str, transport: str, message: str):
    """
    Run the client asynchronously.
    
    Args:
        server_url: Server URL to connect to
        transport: Transport protocol to use
        message: Message to send
    """
    client = Client(server_url, transport)
    
    try:
        await client.initialize()
        response = await client.send_message(message)
        
        print("\n=== Agent Response ===")
        print(response)
        print("======================\n")
        
    except Exception as e:
        logger.error(f"An error occurred: {e}", exc_info=True)
        sys.exit(1)
    finally:
        await client.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nClient stopped by user")
        sys.exit(0)
    except Exception as e:
        logger.error(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
