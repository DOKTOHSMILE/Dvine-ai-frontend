package ai.dvine.ops.task;

/** Speech-input state. Independent of {@link TaskState}. */
public enum MicState {
    MICROPHONE_OFF,
    MICROPHONE_STARTING,
    MICROPHONE_LISTENING,
    SPEECH_DETECTED,
    TRANSCRIBING,
    INTERPRETING_COMMAND
}
