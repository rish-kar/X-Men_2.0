package com.xmen.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Flags class to manage various boolean flags used in the application. This class provides a way to
 * track the state of different operations such as adding, replacing tags, and combining operations.
 */
@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Flags {

  protected boolean add = false;
  protected boolean replaceType = false;
  protected boolean replaceSubmessages = false;
  protected boolean combineAddReplace = false;
  protected boolean combineAddReplaceOnly = false;
  protected boolean switchFlag = false;
  protected boolean trueReplace = false;
  protected boolean forgetMutation = false;
}
