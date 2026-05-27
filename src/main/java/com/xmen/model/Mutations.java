package com.xmen.model;

/**
 * Mutations enum defines the different types of mutations that can be applied to a component. Each
 * mutation represents a specific operation that can be performed on the component's value.
 */
public enum Mutations {
  SKIP_SEND,
  SKIP_RECEIVE,
  SKIP_SEND_RECEIVE,
  SKIP_RECEIVE_SEND,
  SKIP_RECEIVE_SEND_RECEIVE,
  ADD,
  REPLACE_SUB_MESSAGES,
  REPLACE_TYPE,
  COMBINE_ADD_REPLACE_ONLY,
  COMBINE_ADD_REPLACE,
  FORGET,
  NEGLECT
}
