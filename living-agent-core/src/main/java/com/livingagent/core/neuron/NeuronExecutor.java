package com.livingagent.core.neuron;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.ToolCall;
import com.livingagent.core.tool.ToolResult;

import java.util.List;

public interface NeuronExecutor {

    void start(NeuronContext context);

    void onMessage(ChannelMessage message, NeuronContext context);

    ToolResult executeTool(ToolCall call, NeuronContext context);

    List<ToolResult> executeToolsParallel(List<ToolCall> calls, NeuronContext context);

    void abort();

    boolean isRunning();

    int getIterationCount();

    int getMaxIterations();
}
