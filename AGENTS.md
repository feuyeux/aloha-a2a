# AGENTS.md - Agentic Coding Guidelines

This document provides guidelines for agentic coding agents operating in this repository.

## Project Overview

This is a multi-language A2A (Agent-to-Agent) protocol implementation with clients and servers in:
- **JavaScript/TypeScript** (`aloha-js/`)
- **Python** (`aloha-python/`)
- **Java** (`aloha-java/`)
- **Go** (`aloha-go/`)
- **C#** (`aloha-csharp/`)

Each language has both client and server implementations supporting multiple transports (REST, JSON-RPC, gRPC).

---

## Build/Lint/Test Commands

### JavaScript/TypeScript (aloha-js)

```bash
# Navigate to client or server
cd aloha-js/client   # or aloha-js/server

# Install dependencies
npm install

# Development with hot reload
npm run dev

# Build TypeScript
npm run build

# Run all tests
npm test

# Run a single test file
npx vitest run src/path/to/test-file.test.ts

# Run a single test by name
npx vitest run -t "test name"

# Lint code
npm run lint

# Format code
npm run format
```

### Python (aloha-python)

```bash
# Navigate to client or server
cd aloha-python/client   # or aloha-python/server

# Install with dev dependencies
pip install -e ".[dev]"

# Run all tests
pytest

# Run a single test file
pytest tests/test_file.py

# Run a single test function
pytest tests/test_file.py::test_function_name

# Run tests matching a pattern
pytest -k "test_pattern"

# Lint with ruff
ruff check .

# Format with ruff
ruff format .
```

### Java (aloha-java)

```bash
# Build all modules
cd aloha-java
mvn clean install

# Run a single module
cd aloha-java/server
mvn clean compile

# Format code (spotify fmt plugin)
mvn com.spotify.fmt:fmt-maven-plugin:format

# Run tests
mvn test
```

---

## Code Style Guidelines

### TypeScript/JavaScript

**Formatting & Linting**
- Use Prettier for formatting (`npm run format`)
- Use ESLint for linting (`npm run lint`)
- Follow ESLint rules defined in the project

**TypeScript Configuration**
- Strict mode is enabled in `tsconfig.json`
- Target: ES2022, Module: ESNext
- Always use explicit types for function parameters and return types
- Enable `strict: true` in tsconfig

**Naming Conventions**
- Classes: `PascalCase` (e.g., `AlohaClient`)
- Functions/methods: `camelCase` (e.g., `sendMessage`, `_initRest`)
- Private methods: prefix with underscore (e.g., `_processStreamEvent`)
- Constants: `UPPER_SNAKE_CASE`
- Interfaces/types: `PascalCase` with descriptive names

**Imports**
- Use named imports from packages: `import { A2AClient, Transport } from '@a2a-js/sdk'`
- Group imports: external packages first, then internal modules
- Use absolute imports when possible

**Error Handling**
- Always use try/catch for async operations
- Log errors with meaningful context before re-throwing
- Throw descriptive Error objects with messages

```typescript
try {
    await this.client.connect();
} catch (error) {
    console.error('Failed to connect:', error);
    throw new Error(`Connection failed: ${error}`);
}
```

**Async/Await**
- Always use async/await over raw Promises
- Use `for await...of` for async iterators/streams
- Handle async errors with try/catch

**Documentation**
- Use JSDoc comments for public APIs
- Document function params and return types

```typescript
/**
 * Send a message to the agent and wait for response.
 * @param messageText - The message text to send
 * @param contextId - Optional context ID for conversation continuity
 * @returns The agent's response
 */
async sendMessage(messageText: string, contextId?: string): Promise<string>
```

---

### Python

**Formatting & Linting**
- Use Ruff for both linting and formatting
- Line length: 100 characters
- Target Python: 3.11+

**Configuration (pyproject.toml)**
```toml
[tool.ruff]
line-length = 100
target-version = "py311"

[tool.ruff.lint]
select = ["E", "F", "I", "N", "W"]
ignore = ["E501"]
```

**Naming Conventions**
- Classes: `PascalCase` (e.g., `AlohaClient`)
- Functions/methods: `snake_case` (e.g., `send_message`)
- Constants: `UPPER_SNAKE_CASE`
- Private methods: prefix with underscore (e.g., `_process_event`)

**Type Hints**
- Use Python 3.11+ type hints (including `Self`, `LiteralString`, etc.)
- Enable strict mypy checking when possible

**Error Handling**
- Use custom exception classes when appropriate
- Always log errors with context

---

### Java

**Formatting**
- Use Spotify's fmt-maven-plugin for formatting
- Run `mvn com.spotify.fmt:fmt-maven-plugin:format`

**Naming Conventions**
- Classes: `PascalCase`
- Methods: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: lowercase with dots (e.g., `com.aloha.a2a.client`)

**Build**
- Java 21 required
- Use Maven for build management

---

## General Guidelines

1. **Never commit secrets** - Do not commit `.env` files, credentials, or API keys
2. **Write tests** - All new features should have corresponding tests
3. **Run linters before committing** - Always run lint/format commands before committing
4. **Follow existing patterns** - When adding code, follow the style of existing code in the same file/module
5. **Use descriptive names** - Variable and function names should clearly indicate their purpose
6. **Keep functions small** - Single responsibility principle; functions should do one thing well
7. **Handle errors explicitly** - Don't swallow errors; always log or re-throw with context

---

## Testing Guidelines

- Place tests in a `__tests__/` or `tests/` directory (Python) or alongside source files with `.test.ts` extension (TypeScript)
- Test files should mirror the source file structure
- Use descriptive test names that explain what is being tested
- Follow AAA pattern: Arrange, Act, Assert

---

## Notes

- This is an A2A (Agent-to-Agent) protocol implementation
- Each language module supports multiple transports: REST, JSON-RPC, and gRPC
- The project uses the `@a2a-js/sdk` for JavaScript/TypeScript implementations
