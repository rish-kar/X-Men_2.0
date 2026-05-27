package com.xmen.model;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * InMemoryMultipartFile is an implementation of the MultipartFile interface that allows handling
 * file uploads in memory. It is useful for testing or when you want to avoid storing files on disk.
 */
public class InMemoryMultipartFile implements MultipartFile {

  private final byte[] content;
  private final String name;
  private final String originalFilename;
  private final String contentType;

  /**
   * Constructor for InMemoryMultipartFile.
   *
   * @param name the name of the file
   * @param originalFilename the original filename of the file
   * @param contentType the content type of the file
   * @param content the byte array representing the file content
   */
  public InMemoryMultipartFile(
      String name, String originalFilename, String contentType, byte[] content) {
    this.name = name;
    this.originalFilename = originalFilename;
    this.contentType = contentType;
    this.content = (content != null) ? content : new byte[0];
  }

  /**
   * Returns the name of the file.
   *
   * @return the name of the file
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * Returns the original filename of the file.
   *
   * @return the original filename of the file
   */
  @Override
  public String getOriginalFilename() {
    return originalFilename;
  }

  /**
   * Returns the content type of the file.
   *
   * @return the content type of the file
   */
  @Override
  public String getContentType() {
    return contentType;
  }

  /**
   * Checks if the file is empty.
   *
   * @return true if the file is empty, false otherwise
   */
  @Override
  public boolean isEmpty() {
    return content.length == 0;
  }

  /**
   * Returns the size of the file in bytes.
   *
   * @return the size of the file in bytes
   */
  @Override
  public long getSize() {
    return content.length;
  }

  /**
   * Returns the byte array representing the file content.
   *
   * @return the byte array representing the file content
   * @throws IOException if an I/O error occurs
   */
  @Override
  public byte[] getBytes() throws IOException {
    return content;
  }

  /**
   * Returns an InputStream for reading the file content.
   *
   * @return an InputStream for reading the file content
   * @throws IOException if an I/O error occurs
   */
  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(content);
  }

  /**
   * Returns a Resource representing the file.
   *
   * @return a Resource representing the file
   */
  @Override
  public Resource getResource() {
    return MultipartFile.super.getResource();
  }

  /**
   * Transfers the file content to a specified destination file.
   *
   * @param dest the destination file to which the content will be transferred
   * @throws IOException if an I/O error occurs during the transfer
   * @throws IllegalStateException if the transfer cannot be completed
   */
  @Override
  public void transferTo(File dest) throws IOException, IllegalStateException {}

  /**
   * Transfers the file content to a specified destination path.
   *
   * @param dest the destination path to which the content will be transferred
   * @throws IOException if an I/O error occurs during the transfer
   */
  @Override
  public void transferTo(Path dest) throws IOException {
    Files.write(dest, content);
  }
}
