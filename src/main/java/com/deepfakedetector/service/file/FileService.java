package com.deepfakedetector.service.file;

import com.deepfakedetector.exception.DeepfakeException;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;


public interface FileService {


    String uploadFile(String path, MultipartFile file) throws IOException, DeepfakeException;


    InputStream getResourceFile(String path, String name) throws FileNotFoundException, DeepfakeException;


    boolean isValidFileType(MultipartFile file, List<String> allowedExtensions);


    boolean deleteFile(String path) throws IOException;

    boolean createDirectory(String path) throws IOException;


    String generateUniqueFileName(String originalFilename);


    String getReadableFileSize(MultipartFile file);

    boolean fileExists(String path);


    List<String> listFiles(String directoryPath) throws IOException;


    String getFileExtension(String filename);
}