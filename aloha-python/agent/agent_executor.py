"""Dice Agent Executor implementing A2A protocol with LLM integration."""

import logging
import os
from typing import Any, Dict

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.tasks import TaskUpdater
from a2a.types import JSONRPCError, Message, Part, TaskState, TextPart

from tools import check_prime, roll_dice

logger = logging.getLogger(__name__)


# A2A Protocol Error Codes
class A2AErrorCode:
    """Standard A2A protocol error codes."""
    INVALID_REQUEST = "invalid_request"
    UNSUPPORTED_OPERATION = "unsupported_operation"
    TASK_NOT_FOUND = "task_not_found"
    INTERNAL_ERROR = "internal_error"
    TIMEOUT = "timeout"
    CANCELED = "canceled"
    VALIDATION_ERROR = "validation_error"


def validate_message(message: Message) -> None:
    """
    Validate incoming message structure.
    
    Args:
        message: The message to validate
        
    Raises:
        JSONRPCError: If message is invalid
    """
    if not message:
        raise JSONRPCError(
            code=-32602,
            message="Invalid message: message is None",
            data={"error_code": A2AErrorCode.INVALID_REQUEST}
        )
    
    if not message.parts or len(message.parts) == 0:
        raise JSONRPCError(
            code=-32602,
            message="Invalid message: no message parts provided",
            data={"error_code": A2AErrorCode.INVALID_REQUEST}
        )
    
    # Check for at least one text part
    has_text = any(isinstance(part, TextPart) for part in message.parts)
    if not has_text:
        raise JSONRPCError(
            code=-32602,
            message="Invalid message: no text content found in message parts",
            data={"error_code": A2AErrorCode.INVALID_REQUEST}
        )


def validate_tool_parameters(tool_name: str, **kwargs) -> None:
    """
    Validate tool parameters before execution.
    
    Args:
        tool_name: Name of the tool
        **kwargs: Tool parameters
        
    Raises:
        ValueError: If parameters are invalid
    """
    if tool_name == "roll_dice":
        sides = kwargs.get("sides")
        if sides is None:
            raise ValueError("roll_dice requires 'sides' parameter")
        if not isinstance(sides, int):
            raise ValueError(f"'sides' must be an integer, got {type(sides).__name__}")
        if sides <= 0:
            raise ValueError(f"'sides' must be positive, got {sides}")
        if sides > 1000000:
            raise ValueError(f"'sides' must be <= 1000000, got {sides}")
    
    elif tool_name == "check_prime":
        numbers = kwargs.get("numbers")
        if numbers is None:
            raise ValueError("check_prime requires 'numbers' parameter")
        if not isinstance(numbers, list):
            raise ValueError(f"'numbers' must be a list, got {type(numbers).__name__}")
        if len(numbers) == 0:
            raise ValueError("'numbers' list cannot be empty")
        if len(numbers) > 1000:
            raise ValueError(f"'numbers' list too large (max 1000), got {len(numbers)}")
        for num in numbers:
            if not isinstance(num, int):
                raise ValueError(f"All numbers must be integers, got {type(num).__name__}")
            if num < 0:
                raise ValueError(f"All numbers must be non-negative, got {num}")


class DiceAgentExecutor(AgentExecutor):
    """
    Agent executor for the Dice Agent.
    Processes requests using LLM with tool support.
    """
    
    def __init__(self):
        """Initialize the agent executor with LLM integration."""
        self.tools = {
            "roll_dice": roll_dice,
            "check_prime": check_prime,
        }
        # LLM integration will be added here
        self._setup_llm()
    
    def _setup_llm(self):
        """Setup LLM with tool definitions."""
        try:
            # Import Ollama for LLM integration
            import ollama
            
            # Read configuration from environment
            self.base_url = os.getenv('OLLAMA_BASE_URL', 'http://localhost:11434')
            self.model = os.getenv('OLLAMA_MODEL', 'qwen2.5')
            
            # Initialize Ollama client
            self.client = ollama.Client(host=self.base_url)
            
            # Validate connection on startup
            try:
                # Try to list models to verify connection
                self.client.list()
                logger.info(f"Successfully connected to Ollama at {self.base_url}")
                logger.info(f"Using model: {self.model}")
            except Exception as e:
                logger.error(f"Failed to connect to Ollama at {self.base_url}")
                logger.error(f"Please ensure Ollama is installed and running:")
                logger.error(f"1. Install Ollama: https://ollama.ai/download")
                logger.error(f"2. Pull {self.model} model: ollama pull {self.model}")
                logger.error(f"3. Start Ollama service: ollama serve")
                logger.error(f"Error details: {e}")
                raise ConnectionError(f"Cannot connect to Ollama at {self.base_url}: {e}")
            
            # Define tool schemas for LLM
            self.tool_schemas = [
                {
                    "type": "function",
                    "function": {
                        "name": "roll_dice",
                        "description": "Rolls an N-sided dice and returns a random number between 1 and N",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "sides": {
                                    "type": "integer",
                                    "description": "The number of sides on the dice (must be positive)"
                                }
                            },
                            "required": ["sides"]
                        }
                    }
                },
                {
                    "type": "function",
                    "function": {
                        "name": "check_prime",
                        "description": "Checks if the given numbers are prime and returns which ones are prime",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "numbers": {
                                    "type": "array",
                                    "items": {"type": "integer"},
                                    "description": "List of integers to check for primality"
                                }
                            },
                            "required": ["numbers"]
                        }
                    }
                }
            ]
            
            logger.info("LLM setup complete with Ollama qwen2.5")
            
        except ImportError as e:
            logger.error(f"Ollama package not available: {e}")
            logger.error("Please install ollama: pip install ollama")
            raise
        except ConnectionError:
            # Re-raise connection errors
            raise
        except Exception as e:
            logger.error(f"Unexpected error during LLM setup: {e}")
            raise
    
    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        """
        Execute agent logic and emit events.
        
        Args:
            context: The request context containing the message and task
            event_queue: Queue for emitting status update events
            
        Raises:
            JSONRPCError: If execution fails with A2A protocol error
        """
        updater = TaskUpdater(context, event_queue)
        task_id = context.task.id if context.task else "<none>"
        
        try:
            logger.info(f"Received new request. taskId={task_id}")
            
            # Validate incoming request
            try:
                validate_message(context.message)
                logger.debug("Message validation passed")
            except JSONRPCError as e:
                logger.error(f"Message validation failed: {e.message}")
                # Emit failed status with validation error
                error_part = TextPart(text=f"Validation error: {e.message}")
                await updater.add_artifact([error_part])
                await updater.fail()
                raise
            
            # Mark task as submitted and start working
            if context.task is None:
                logger.debug("No task in context; marking submitted")
                await updater.submit()
                logger.info("Task submitted")
            
            await updater.start_work()
            logger.info(f"Task started working: {task_id}")
            
            # Extract text from message
            message_text = self._extract_text_from_message(context.message)
            logger.debug(f"Extracted message text: {message_text}")
            
            if not message_text or not message_text.strip():
                logger.warning("Empty message text received")
                error_part = TextPart(text="Error: Empty message received. Please provide a message.")
                await updater.add_artifact([error_part])
                await updater.fail()
                raise JSONRPCError(
                    code=-32602,
                    message="Empty message text",
                    data={"error_code": A2AErrorCode.INVALID_REQUEST}
                )
            
            # Process with LLM
            logger.info("Invoking LLM with tools")
            try:
                response = await self._process_with_llm(message_text)
                logger.info(f"LLM returned response length={len(response) if response else 0}")
                logger.debug(f"LLM response content: {response}")
            except ValueError as e:
                # Tool validation error
                logger.error(f"Tool validation error: {e}", exc_info=True)
                error_part = TextPart(text=f"Invalid request: {str(e)}")
                await updater.add_artifact([error_part])
                await updater.fail()
                raise JSONRPCError(
                    code=-32602,
                    message=str(e),
                    data={"error_code": A2AErrorCode.VALIDATION_ERROR}
                )
            except Exception as e:
                # LLM processing error
                logger.error(f"LLM processing error: {e}", exc_info=True)
                error_part = TextPart(text=f"Error processing your request: {str(e)}")
                await updater.add_artifact([error_part])
                await updater.fail()
                raise JSONRPCError(
                    code=-32603,
                    message=f"LLM processing failed: {str(e)}",
                    data={"error_code": A2AErrorCode.INTERNAL_ERROR}
                )
            
            # Create response part
            response_part = TextPart(text=response)
            parts = [response_part]
            
            # Add artifact and complete task
            logger.info(f"Adding artifact to task and completing. partsCount={len(parts)}")
            await updater.add_artifact(parts)
            logger.debug("Artifact added")
            await updater.complete()
            logger.info(f"Task completed successfully: {task_id}")
            
        except JSONRPCError:
            # Re-raise A2A protocol errors
            logger.error(f"A2A protocol error while executing task {task_id}", exc_info=True)
            raise
        except Exception as e:
            # Catch-all for unexpected errors
            logger.error(f"Unexpected error during agent execution for task {task_id}: {e}", exc_info=True)
            try:
                error_part = TextPart(text=f"Internal server error: {str(e)}")
                await updater.add_artifact([error_part])
                await updater.fail()
                logger.info(f"Marked task as failed after unexpected error: {task_id}")
            except Exception as inner:
                logger.warning(f"Failed to update task after error: {inner}", exc_info=True)
            
            # Return A2A protocol error
            raise JSONRPCError(
                code=-32603,
                message=f"Internal error: {str(e)}",
                data={"error_code": A2AErrorCode.INTERNAL_ERROR}
            )
    
    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        """
        Cancel ongoing execution.
        
        Args:
            context: The request context containing the task
            event_queue: Queue for emitting status update events
            
        Raises:
            JSONRPCError: If task cannot be canceled
        """
        task = context.task
        
        if not task:
            logger.error("Cancel requested but no task in context")
            raise JSONRPCError(
                code=-32602,
                message="No task found in context",
                data={"error_code": A2AErrorCode.TASK_NOT_FOUND}
            )
        
        task_id = task.id
        logger.info(f"Cancel requested for task: {task_id}")
        
        # Check if task can be canceled
        if task.status.state == TaskState.CANCELED:
            logger.warning(f"Task already cancelled: {task_id}")
            raise JSONRPCError(
                code=-32001,
                message=f"Task {task_id} is already canceled",
                data={"error_code": A2AErrorCode.CANCELED}
            )
        
        if task.status.state == TaskState.COMPLETED:
            logger.warning(f"Task already completed (cannot cancel): {task_id}")
            raise JSONRPCError(
                code=-32001,
                message=f"Task {task_id} is already completed and cannot be canceled",
                data={"error_code": A2AErrorCode.UNSUPPORTED_OPERATION}
            )
        
        if task.status.state == TaskState.FAILED:
            logger.warning(f"Task already failed (cannot cancel): {task_id}")
            raise JSONRPCError(
                code=-32001,
                message=f"Task {task_id} has already failed and cannot be canceled",
                data={"error_code": A2AErrorCode.UNSUPPORTED_OPERATION}
            )
        
        # Cancel the task
        try:
            updater = TaskUpdater(context, event_queue)
            await updater.cancel()
            logger.info(f"Task cancelled successfully: {task_id}")
        except Exception as e:
            logger.error(f"Error canceling task {task_id}: {e}", exc_info=True)
            raise JSONRPCError(
                code=-32603,
                message=f"Failed to cancel task: {str(e)}",
                data={"error_code": A2AErrorCode.INTERNAL_ERROR}
            )
    
    def _extract_text_from_message(self, message: Message) -> str:
        """
        Extract text content from message parts.
        
        Args:
            message: The message to extract text from
            
        Returns:
            Concatenated text from all text parts
        """
        text_parts = []
        if message.parts:
            for part in message.parts:
                if isinstance(part, TextPart):
                    text_parts.append(part.text)
        return "".join(text_parts)
    
    async def _process_with_llm(self, message_text: str) -> str:
        """
        Process message with LLM and execute tools as needed.
        
        Args:
            message_text: The user's message
            
        Returns:
            The agent's response
        """
        if not hasattr(self, 'client') or not self.client:
            # Fallback mode without LLM
            return self._fallback_processing(message_text)
        
        try:
            system_message = """You are a dice rolling agent that can roll arbitrary N-sided dice and check if numbers are prime.

When asked to roll a dice, call the roll_dice tool with the number of sides as an integer parameter.

When asked to check if numbers are prime, call the check_prime tool with a list of integers.

When asked to roll a dice and check if the result is prime:
1. First call roll_dice to get the result
2. Then call check_prime with the result from step 1
3. Include both the dice result and prime check in your response

Always use the tools - never try to roll dice or check primes yourself.
Be conversational and friendly in your responses.

你是一个骰子代理，可以投掷任意面数的骰子并检查数字是否为质数。
当被要求投掷骰子时，使用 roll_dice 工具。
当被要求检查质数时，使用 check_prime 工具。
始终使用工具，不要自己计算。"""
            
            messages = [
                {"role": "system", "content": system_message},
                {"role": "user", "content": message_text}
            ]
            
            # Call LLM with tools
            response = await self._call_llm_with_tools(messages)
            return response
            
        except Exception as e:
            logger.error(f"Error processing with LLM: {e}", exc_info=True)
            raise
    
    async def _call_llm_with_tools(self, messages: list) -> str:
        """
        Call LLM with tool support and execute tools as needed.
        
        Args:
            messages: Conversation messages
            
        Returns:
            Final response after tool execution
        """
        max_iterations = 5
        iteration = 0
        
        while iteration < max_iterations:
            iteration += 1
            
            try:
                # Call Ollama chat API with tools
                response = self.client.chat(
                    model=self.model,
                    messages=messages,
                    tools=self.tool_schemas
                )
                
                message = response.get('message', {})
                
                # Check if LLM wants to call tools
                tool_calls = message.get('tool_calls')
                if tool_calls:
                    # Add assistant message with tool calls to conversation
                    messages.append(message)
                    
                    # Execute tool calls
                    for tool_call in tool_calls:
                        function = tool_call.get('function', {})
                        tool_name = function.get('name')
                        tool_args = function.get('arguments', {})
                        
                        logger.info(f"Executing tool: {tool_name} with args: {tool_args}")
                        
                        # Execute the tool
                        if tool_name in self.tools:
                            # Validate tool parameters
                            try:
                                validate_tool_parameters(tool_name, **tool_args)
                            except ValueError as ve:
                                logger.error(f"Tool parameter validation failed: {ve}")
                                raise
                            
                            tool_result = self.tools[tool_name](**tool_args)
                            logger.info(f"Tool result: {tool_result}")
                            
                            # Add tool response to messages
                            messages.append({
                                "role": "tool",
                                "content": str(tool_result)
                            })
                        else:
                            logger.warning(f"Unknown tool requested: {tool_name}")
                            messages.append({
                                "role": "tool",
                                "content": f"Error: Unknown tool '{tool_name}'"
                            })
                    
                    # Continue loop to get final response
                    continue
                
                # No more tool calls, return final response
                content = message.get('content', '')
                return content or "I processed your request."
                
            except Exception as e:
                logger.error(f"Error in LLM call iteration {iteration}: {e}", exc_info=True)
                # Check if it's a connection error
                if "connection" in str(e).lower() or "refused" in str(e).lower():
                    raise ConnectionError(f"Failed to connect to Ollama at {self.base_url}. Please ensure Ollama is running.")
                raise
        
        return "Maximum iterations reached while processing request."
    
    def _fallback_processing(self, message_text: str) -> str:
        """
        Fallback processing without LLM (simple pattern matching).
        
        Args:
            message_text: The user's message
            
        Returns:
            Response based on simple pattern matching
        """
        message_lower = message_text.lower()
        
        # Simple pattern matching for dice rolling
        if "roll" in message_lower and "dice" in message_lower:
            # Try to extract number
            import re
            match = re.search(r'(\d+)[-\s]?sided', message_lower)
            if match:
                sides = int(match.group(1))
                result = roll_dice(sides)
                return f"I rolled a {sides}-sided dice and got: {result}"
            else:
                # Default to 6-sided
                result = roll_dice(6)
                return f"I rolled a 6-sided dice and got: {result}"
        
        # Simple pattern matching for prime checking
        if "prime" in message_lower:
            import re
            numbers = [int(n) for n in re.findall(r'\b\d+\b', message_text)]
            if numbers:
                result = check_prime(numbers)
                return result
            else:
                return "Please provide numbers to check for primality."
        
        return "I can roll dice and check if numbers are prime. What would you like me to do?"
