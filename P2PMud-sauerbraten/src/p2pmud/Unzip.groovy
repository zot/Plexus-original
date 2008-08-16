package p2pmud

import java.io.*;
import java.util.*;
import java.util.zip.*;


public class Unzip 
{
  def static copyInputStream(InputStream inp, OutputStream out)
  {
    byte[] buffer = new byte[1024];
    int len;

    while((len = inp.read(buffer)) >= 0)
      out.write(buffer, 0, len);

    inp.close();
    out.close();
  }

  public static Unzipper(fn, topFolder, folders) {
    try {
      def zipFile = new ZipFile(fn);

      def entries = zipFile.entries();

      while(entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry)entries.nextElement();

        /*if(entry.isDirectory()) {
          // Assume directories are stored parents first then children.
          System.err.println("Extracting directory: " + entry.getName());
          // This is not robust, just for demonstration purposes.
          (new File(entry.getName())).mkdir();
          continue;
        }*/
        def name = new File(topFolder, entry.getName().toString())
        //println name
        if (!folders) {
        	name = new File(topFolder, new File(entry.getName()).getName().toString()) // chop off folders in front of name
        }
        //System.err.println("Extracting file: " + name);
        copyInputStream(zipFile.getInputStream(entry),
           new BufferedOutputStream(new FileOutputStream(name)));
      }

      zipFile.close();
    } catch (IOException ioe) {
      System.err.println("Unhandled exception:");
      ioe.printStackTrace();
      return;
    }
  }

}
