"""Entry point for running the Dice Agent."""

import asyncio
import sys

try:
    from .agent import main
except ImportError:
    from server.agent import main

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nServer stopped by user")
        sys.exit(0)
    except Exception as e:
        print(f"Fatal error: {e}", file=sys.stderr)
        sys.exit(1)
