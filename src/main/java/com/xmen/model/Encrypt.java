package com.xmen.model;

import java.util.Objects;

/**
 * Encrypt class represents an encrypted message, which consists of a message and a key. This class
 * implements the Message interface and provides methods to represent the encrypted message, check
 * equality, and retrieve the message and key.
 */
public class Encrypt implements Message {

  private final Message msg;
  private final Message key;

  /**
   * Constructor for Encrypt.
   *
   * @param msg the message to be encrypted
   * @param key the key used for encryption
   */
  public Encrypt(Message msg, Message key) {
    this.msg = msg;
    this.key = key;
  }

  /**
   * Represents the encrypted message in the format "{msg}_{key}".
   *
   * @return a string representation of the encrypted message
   */
  @Override
  public String represent() {
    return "{" + msg.represent() + "}_{" + key.represent() + "}";
  }

  /**
   * Checks if this encrypted message is equal to another object.
   *
   * @param o the object to compare with
   * @return true if the object is an Encrypt and both message and key are equal, false otherwise
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Encrypt)) {
      return false;
    }
    Encrypt e = (Encrypt) o;
    return msg.equals(e.msg) && key.equals(e.key);
  }

  /**
   * Generates a hash code for this encrypted message based on the hash codes of its message and
   * key.
   *
   * @return the hash code of the encrypted message
   */
  @Override
  public int hashCode() {
    return Objects.hash(msg, key);
  }

  /**
   * Returns the message that is encrypted.
   *
   * @return the encrypted message
   */
  public Message getMsg() {
    return msg;
  }

  /**
   * Returns the key used for encryption.
   *
   * @return the encryption key
   */
  public Message getKey() {
    return key;
  }

  /**
   * Returns a string representation of the Encrypt object.
   *
   * @return a string in the format "Encrypt(msg, key)"
   */
  @Override
  public String toString() {
    return "Encrypt(" + msg + ", " + key + ")";
  }
}
