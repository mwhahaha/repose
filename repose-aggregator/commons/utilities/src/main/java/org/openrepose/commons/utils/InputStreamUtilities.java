package org.openrepose.commons.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class InputStreamUtilities {
    
    private InputStreamUtilities(){
    }

   public static String streamToString(InputStream stream) throws IOException {
      String stringValue = "";

      if (stream != null) {
         final StringBuilder stringBuilder = new StringBuilder();

         final BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));

         String nextLine;

         while ((nextLine = in.readLine()) != null) {
            stringBuilder.append(nextLine);
         }

         stringValue = stringBuilder.toString();
      }

      return stringValue;
   }
}
