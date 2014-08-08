/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

// Depends on the logger to be properly initialized.
public class ProgramBanner
{
    private static final Logger l = LoggerFactory.getLogger(ProgramBanner.class);

    private ProgramBanner()
    {
        // prevents instantiation
    }

    public static void logBanner(String banneFile)
    {
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(banneFile), "UTF-8");

            while (scanner.hasNextLine()) {
                l.info("{}", scanner.nextLine());
            }
        } catch (FileNotFoundException e) {
            l.warn("Banner file not found: {}", e.toString());
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }
}
