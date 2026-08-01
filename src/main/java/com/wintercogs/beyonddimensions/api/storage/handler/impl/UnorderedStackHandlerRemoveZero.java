package com.wintercogs.beyonddimensions.api.storage.handler.impl;

public class UnorderedStackHandlerRemoveZero extends AbstractUnorderedStackHandler {

    public UnorderedStackHandlerRemoveZero(UiTimestampPolicy uiTimestampPolicy) {
        super(ZeroPolicy.REMOVE_ON_ZERO, uiTimestampPolicy);
    }

    public UnorderedStackHandlerRemoveZero(UiTimestampPolicy uiTimestampPolicy, long slotCapacity, int slotMaxSize) {
        this(uiTimestampPolicy);
        this.slotCapacity = slotCapacity;
        this.slotMaxSize = slotMaxSize;
    }
}
