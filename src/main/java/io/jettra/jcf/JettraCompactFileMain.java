package io.jettra.jcf;

import io.jettra.jcf.io.JCFFileHandler;

import java.io.File;
import java.util.Scanner;

/**
 * Main entry point for JettraCompactFile CLI.
 */
public class JettraCompactFileMain {

    public static void main(String[] args) {
        System.out.println("=== JettraCompactFile v1.0 (Java 25) ===");
        
        if (args.length < 3) {
            printUsage();
            return;
        }

        String command = args[0];
        String inputPath = args[1];
        String key = args[2];

        try {
            if ("compress".equalsIgnoreCase(command)) {
                String outputPath = inputPath + ".jettracf";
                System.out.println("Compressing " + inputPath + "...");
                long start = System.currentTimeMillis();
                JCFFileHandler.compressFile(new File(inputPath), new File(outputPath), key);
                long end = System.currentTimeMillis();
                System.out.println("Compression completed in " + (end - start) + "ms");
                System.out.println("Output: " + outputPath);
            } else if ("decompress".equalsIgnoreCase(command)) {
                File inputFile = new File(inputPath);
                File outputDir = inputFile.getParentFile();
                if (outputDir == null) {
                    outputDir = new File(".");
                }
                System.out.println("Decompressing " + inputPath + "...");
                long start = System.currentTimeMillis();
                JCFFileHandler.decompressFile(inputFile, outputDir, key);
                long end = System.currentTimeMillis();
                System.out.println("Decompression completed in " + (end - start) + "ms");
            } else {
                System.out.println("Unknown command: " + command);
                printUsage();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar JettraCompactFile.jar compress <file> <key>");
        System.out.println("  java -jar JettraCompactFile.jar decompress <file.jettracf> <key>");
    }
}
