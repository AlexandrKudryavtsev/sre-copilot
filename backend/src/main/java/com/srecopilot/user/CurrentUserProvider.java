package com.srecopilot.user;

/** Identity source to replace when authentication is introduced. */
public interface CurrentUserProvider {

    long userId();
}
