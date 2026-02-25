"""Main entry point for the A2A Host client."""

import asyncio
import logging
import sys
import json

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
    help='Agent port (default: 13002 for REST, 13001 for JSON-RPC, 13000 for gRPC)'
)
@click.option(
    '--transport',
    type=click.Choice(['jsonrpc', 'grpc', 'rest'], case_sensitive=False),
    default='rest',
    help='Transport protocol (default: rest)'
)
@click.option(
    '--message',
    default='Roll a 6-sided dice',
    help='Message to send (default: "Roll a 6-sided dice")'
)
@click.option(
    '--probe',
    is_flag=True,
    help='Probe transport capabilities and exit'
)
def main(host: str, port: int, transport: str, message: str, probe: bool):
    """
    A2A Host client for sending messages to agents.
    
    Examples:
    
    # Send message using REST (default)
      python -m host --message "Roll a 20-sided dice"
    
      # Send message using JSON-RPC
      python -m host --transport jsonrpc --port 13001 --message "Is 17 prime?"
    
      # Send message using REST
      python -m host --transport rest --port 13002 --message "Check if 2, 7, 11 are prime"
    """
    # Set default port based on transport if not specified
    if port is None:
        if transport == 'rest':
            port = 13002
        elif transport == 'jsonrpc':
            port = 13001
        elif transport == 'grpc':
            port = 13000
    
    # Build server URL
    server_url = build_server_url(host, port, transport)
    
    logger.info("Configuration:")
    logger.info(f"  Host: {host}")
    logger.info(f"  Port: {port}")
    logger.info(f"  Transport: {transport}")
    logger.info(f"  Server URL: {server_url}")
    logger.info(f"  Probe: {probe}")
    if not probe:
        logger.info(f"  Message: {message}")
    
    # Run async client
    asyncio.run(run_client(server_url, transport, message, probe))


async def run_client(server_url: str, transport: str, message: str, probe: bool):
    """
    Run the client asynchronously.
    
    Args:
        server_url: Server URL to connect to
        transport: Transport protocol to use
        message: Message to send
    """
    client = Client(server_url, transport)
    
    try:
        if transport != 'rest':
            raise RuntimeError(
                f"Transport '{transport}' is not fully supported by this host implementation. "
                "Please use --transport rest."
            )

        await client.initialize()

        if probe:
            capabilities = await client.probe_transports()
            print("\n=== Transport Capabilities ===")
            print(json.dumps(capabilities, indent=2, ensure_ascii=False))
            print("==============================\n")
            return

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
