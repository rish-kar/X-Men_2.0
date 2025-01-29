package com.sermas.x.men.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Flags {

    protected boolean add = false;
    protected boolean replaceTags = false;
    protected boolean replaceSubmessages = false;
    protected boolean combineAddReplace = false;
    protected boolean combineAddReplaceOnly = false;
    protected boolean switchFlag = false;

}
