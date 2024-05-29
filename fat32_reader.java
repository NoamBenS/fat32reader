import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class fat32_reader {

    /* FILE AND DIRECTORY VARIABLES */
    private static RandomAccessFile raf = null;
    private static String directory = "/]";

    /* GENERAL VALUES */
    private static long bytesPerSec;
    private static long secPerClus;
    private static long rsvdSecCnt;
    private static long numFats;
    private static long secPerFat;

    /* VARIABLES RELATING TO ABSOLUTE POSITIONING */
    private static long rootClus;
    private static long offset;
    private static long currentCluster;

    /**
     * This is the main method that runs the program
     * 
     * @param args the original arguments of the commandline (should be the image
     *             file name)
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java fat32_reader <image file>");
            System.exit(1);
        }
        createFile(args[0]);
        prepInfo();
        loop();
    }

    /**
     * Create a file object, or throw an error if it doesn't work.
     * 
     * @param file the filepath (from root directory) of the img file
     */
    private static void createFile(String file) {
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (Exception e) {
            System.out.printf("special device %s does not exist\n", file);
            System.exit(1);
        }
    }

    /**
     * This method loops the user in a command line interface and serves as the
     * program junction
     * 
     * @throws IOException
     */
    private static void loop() {
        Scanner reader = new Scanner(System.in);
        String input = null;
        while (true) {

            // print current directory for functionality
            System.out.printf("%s ", directory);

            // read line and continue if empty
            input = reader.nextLine();
            while (input == null) {
                input = reader.nextLine();
            }
            if (input.isEmpty()) {
                continue;
            }

            // parsing command
            String[] command = input.split(" ");
            command[0] = command[0].toLowerCase();
            if (command[0].equals("stop")) {
                reader.close();
                System.exit(0);
            } else if (command[0].equals("info")) {
                info();
            } else if (command[0].equals("ls")) {
                ls();
            }
            // the following commands MUST have a second argument
            else if (command[0].equals("cd")) {
                if (command.length != 2) {
                    System.out.println("Error: incorrect number of operands");
                    continue;
                }
                cd(command[1].toUpperCase());
            } else if (command[0].equals("stat")) {
                if (command.length != 2) {
                    System.out.println("Error: incorrect number of operands");
                    continue;
                }
                stat(command[1].toUpperCase());
            } else if (command[0].equals("read")) {
                if (command.length != 4) {
                    System.out.println("Error: incorrect number of operands");
                    continue;
                }
                read(command[1].toUpperCase(), command[2], command[3]);
            } else if (command[0].equals("size")) {
                if (command.length != 2) {
                    System.out.println("Error: incorrect number of operands");
                    continue;
                }
                size(command[1].toUpperCase());
            } else {
                System.out.println("Command '" + command[0] + "' not found");
            }
        }
    }

    /**
     * This method prints the important information stored in the boot sector
     */
    public static void info() {
        System.out.printf("BPB_BytesPerSec is 0x%x, %d\n", bytesPerSec, bytesPerSec);
        System.out.printf("BPB_SecPerClus is 0x%x, %d\n", secPerClus, secPerClus);
        System.out.printf("BPB_RsvdSecCnt is 0x%x, %d\n", rsvdSecCnt, rsvdSecCnt);
        System.out.printf("BPB_NumFATs is 0x%x, %d\n", numFats, numFats);
        System.out.printf("BPB_FATSz32 is 0x%x, %d\n", secPerFat, secPerFat);
    }

    /**
     * This method reads the necessary information from the file in the boot sector
     * and saves them to global variables
     */
    private static void prepInfo() {
        // read everything
        try {
            raf.seek(11); // starts at 11 to read bytes per sector (always 512 but for safety)
            bytesPerSec = raf.read() | raf.read() << 8;
            raf.seek(13); // sets to 13 to read sectors per cluster
            secPerClus = raf.read();
            raf.seek(14); // sets to 14 to read reserved sector count
            rsvdSecCnt = raf.read() | raf.read() << 8;
            raf.seek(16); // sets to 16 to read number of FATs (always 2 but for safety)
            numFats = raf.read();
            raf.seek(36); // sets to 36 to read FAT size
            secPerFat = raf.read() | raf.read() << 8 | raf.read() << 16 | raf.read() << 24;
            raf.seek(44); // sets to 45 to read root cluster
            rootClus = raf.read() | raf.read() << 8 | raf.read() << 16 | raf.read() << 24;
            currentCluster = rootClus;
        } catch (Exception e) {
            System.out.println("Error reading file");
            return;
        }
        offset = (rsvdSecCnt + (numFats * secPerFat)) * bytesPerSec;
    }

    /**
     * This method calculates the absolute location (byte #) of a cluster
     * 
     * @param currentCluster2 the cluster of information you're looking for. note
     *                        that
     *                        cluster 2 is the first acc cluster.
     * @return
     */
    private static long absoluteLoc(long currentCluster2) {
        if (currentCluster2 == 0 || currentCluster2 == 1) {
            return -1;
        }
        return (offset + (currentCluster2 - 2) * secPerClus * bytesPerSec);
    }

    public static void ls() {

        // get the list of files in the directory
        List<String> print = getFullFiles(currentCluster);
        if (print == null) {
            System.out.println("Error reading file");
            return;
        }

        // sort the list of files alphabetically
        Collections.sort(print);

        // print the entire list of files (including . and ..)
        System.out.print(". ..");
        for (String s : print) {
            System.out.print(s);
        }
        System.out.println();
    }

    /**
     * This method gets all the files in a cluster and all the clusters that follow
     * 
     * @param cluster the starting cluster
     * @return the list of all the files in the cluster and all the clusters that
     *         follow
     */
    private static List<String> getFullFiles(long cluster) {

        List<String> print = getFiles(cluster, true);

        // if there is more to go in the FAT, keep adding the next cluster's files
        // get value of currCluster that you can overwrite
        long current = currentCluster;
        while (true) {

            // get next cluster number (or -1 if it doesn't exist)
            int next = getNextCluster(current);
            if (next == -1) {
                break;
            }

            // get the files in the next cluster
            List<String> nextFiles = getFiles(next, false);

            // if there was an error reading the file, break
            if (nextFiles == null) {
                System.out.println("Error reading file");
                return null;
            }

            // add the files to the list
            print.addAll(nextFiles);

            // update "current" value
            current = next;
        }
        return print;
    }

    /**
     * This method gets the next cluster in the FAT
     * 
     * @param current the current cluster
     * @return the next cluster, or -1 if it doesn't exist
     */
    private static int getNextCluster(long current) {
        long fatLoc = rsvdSecCnt * bytesPerSec + (current * 4);
        try {
            raf.seek(fatLoc);
            int next = raf.read() | raf.read() << 8 | raf.read() << 16 | raf.read() << 24;
            if (next >= 0x0FFFFFF8 && next <= 0x0FFFFFFF) {
                return -1;
            }
            return next;
        } catch (Exception e) {
            System.out.println("Error reading file");
            return -1;
        }
    }

    /**
     * gets you a list of all the names of entries in a directory
     * 
     * @param start the absolute start of the directory
     * @return a list of all the names of the entries in the directory
     */
    private static List<String> getFiles(long cluster, boolean base) {

        List<String> print = new ArrayList<String>();

        // addition for if you're looking at the root cluster
        int addition = cluster == rootClus ? 0 : 32;

        // innerAdd for if you're looking at an inner cluster vs the base cluster for a
        // directory
        int innerAdd = base == true ? 64 : 0;

        // add em up and you get your absolute start position
        long start = absoluteLoc(cluster) + innerAdd + addition;

        try {

            // remember the max amount i can be has to be the value of the clusters you have
            // minus how many you've already skipped.
            for (int i = 0; i < (secPerClus * bytesPerSec - (addition + innerAdd)); i = i + 64) {

                raf.seek(start);

                // set up the string builder and validity check
                StringBuilder sb = new StringBuilder();
                boolean valid = true;

                for (int j = 0; j < 11; j++) {
                    byte b = raf.readByte();
                    // if it's an empty file or deleted, break
                    if ((j == 0 && b == (byte) 0x00) || b == (byte) 0xE5) {
                        valid = false;
                        break;
                    }

                    // if it's a space, it's a directory or a file but you've gotta skip files
                    else if (b == 0x20) {

                        // save where we were before
                        long prev = start + j + 1;

                        // if it's a dir then we're not printing ish after it
                        if (isDir(start)) {
                            j = 11;
                            continue;
                        }

                        // otherwise add the period and skip the rest of the empty bytes
                        else {
                            raf.seek(prev);
                            sb.append(".");
                            while (j < 7) {
                                raf.readByte();
                                j++;
                            }
                        }
                    }

                    // if it's a char, add it to the string builder
                    else {
                        sb.append((char) b);
                    }
                }

                // if it's a valid file/dir, add it to the list
                if (valid) {
                    // set the space and print it
                    print.add(" " + sb.toString());
                }
                start += 64;
            }
        } catch (Exception e) {
            System.out.println("Error reading file");
            return null;
        }
        return print;
    }

    /**
     * This method checks if the file is a directory
     * 
     * @param start the start byte of the file name
     * @return true iff the file has the directory bit flipped for its attributes
     */
    private static boolean isDir(long start) {
        try {

            // set seek to the byte at which the attributes are stored
            raf.seek(start + 11);
            byte b = raf.readByte();

            // check for hex 10 (0x10) which is the directory bit
            if ((b & 0x10) == 0x10) {
                return true;
            }

        } catch (Exception e) {
            System.out.println("Error reading file");
            return false;
        }
        return false;
    }

    /**
     * This method changes the current directory to the specified path
     * 
     * @param path the path to change to, must be from the working directory.
     */
    public static void cd(String path) {

        // if it's . then do nothing
        if (path.equals(".")) {
            return;
        }

        // if it's .. then go back one
        if (path.equals("..")) {

            // if you're already at the root, do nothing
            if (currentCluster == rootClus) {
                return;
            }

            // get the previous cluster
            long prev = getPrevDirClus();
            if (prev == -1) {
                return;
            }
            if (prev == 0) {
                currentCluster = rootClus;
                directory = "/]";
                return;
            }

            // set the current cluster to the previous cluster
            currentCluster = prev;

            // update the string directory
            directory = directory.substring(0, directory.lastIndexOf("/"));
            directory += "]";
            return;
        }

        // try to find the cluster of the path file from the working directory
        int clust = getFullFileInfo(path, 2);

        // if the path passed in points to a file or a non-existent item, print an error
        if (clust == -1) {
            System.out.println("Error: " + path + " is not a directory");
            return;
        }

        // if the directory does, in fact, exist, update the current cluster and the
        // directory string
        else {
            currentCluster = clust;
            directory = directory.substring(0, directory.lastIndexOf(']'));
            if (directory.equals("/")) {
                directory += path + "]";
            } else {
                directory += "/" + path + "]";
            }
        }

        return;
    }

    /**
     * This method gets the previous cluster of the current cluster
     * 
     * @param currentCluster the current cluster
     * @return the previous cluster, or -1 if it doesn't exist
     */
    private static long getPrevDirClus() {
        long offset = absoluteLoc(currentCluster) + 32;
        try {

            // find the offset of the previous cluster
            raf.seek(offset + 20);
            int prev = raf.read() << 16 | raf.read() << 24;
            raf.seek(offset + 26);
            prev = raf.read() | raf.read() << 8;

            // if it's a special value that means there are no more clusters
            if (prev >= 0x0FFFFFF8 && prev <= 0x0FFFFFFF) {
                return -1;
            }

            return prev;
        } catch (Exception e) {
            System.out.println("Error reading file");
            return -1;
        }
    }

    /**
     * this method gives you the stats of a certain file/directory
     * 
     * @param path the file/directory to check, if it exists
     */
    public static void stat(String path) {
        if (path.equals(".") || path.equals("..")) {
            statForDots(path);
            return;
        }
        int attributes = getFullFileInfo(path, 3);
        if (attributes == -1) {
            System.out.println("Error: file/directory does not exist");
            return;
        }
        int size = getFullFileInfo(path, 0);

        // if it's a directory
        if (size == -1) {
            size = 0;
        }
        int nextClusterNumber = getFullFileInfo(path, 1);
        if (nextClusterNumber == -1) {
            nextClusterNumber = getFullFileInfo(path, 2);
        }

        // print the stats
        System.out.println("Size is " + size);

        // get the attributes in a list
        List<String> attributesList = getAttributes(attributes);

        // if there are no attributes
        if (attributesList.isEmpty()) {
            System.out.println("Attributes NONE");
        }
        // if there ARE attributes
        else {
            System.out.print("Attributes");
            for (int i = 0; i < attributesList.size(); i++) {
                System.out.print(" " + attributesList.get(i));
            }
            System.out.println();
        }
        String formattedVal = String.format("0x%08X", nextClusterNumber);
        System.out.println("Next cluster number is " + formattedVal);
        return;
    }

    private static void statForDots(String path) {

        // set base variables
        int attributes = 0;
        long nextClusterNumber = 0;

        // get the attributes and next cluster number. if it's rootclus then doesn't
        // matter
        if (path.equals(".")) {

            nextClusterNumber = currentCluster;
            // get the attributes
            try {
                raf.seek(absoluteLoc(currentCluster) + 11);
                attributes = raf.readByte();
            } catch (Exception e) {
                System.out.println("Error reading file");
                return;
            }
        }

        // if it's not root or . then it has to refer to a previous directory
        if (path.equals("..")) {
            nextClusterNumber = getPrevDirClus();

            // find attributes
            try {
                raf.seek(absoluteLoc(currentCluster) + 43);
                attributes = raf.readByte();
            } catch (Exception e) {
                System.out.println("Error reading file");
                return;
            }
        }

        if (currentCluster == rootClus) {
            nextClusterNumber = 0;
            attributes = 0x10;
        }

        // print the stats (for a directory, always 0)
        System.out.println("Size is 0");

        // get the attributes in a list
        List<String> attributesList = getAttributes(attributes);

        // if there are no attributes
        if (attributesList.isEmpty()) {
            System.out.println("Attributes NONE");
        }
        // if there ARE attributes
        else {
            System.out.print("Attributes");
            for (String s : attributesList) {
                System.out.print(" " + s);
            }
            System.out.println();
        }
        String formattedVal = String.format("0x%08X", nextClusterNumber);
        System.out.println("Next cluster number is " + formattedVal);
        return;
    }

    /**
     * This method gets the attributes of a file
     * 
     * @param attributes the attributes of the file
     * @return a list of the attributes of the file
     */
    private static List<String> getAttributes(int attributes) {
        byte a = (byte) attributes;
        List<String> list = new ArrayList<String>();
        if ((a & 0x20) == 0x20) {
            list.add("ATTR_ARCHIVE");
        }
        if ((a & 0x10) == 0x10) {
            list.add("ATTR_DIRECTORY");
        }
        if ((a & 0x08) == 0x08) {
            list.add("ATTR_VOLUME_ID");
        }
        if ((a & 0x04) == 0x04) {
            list.add("ATTR_SYSTEM");
        }
        if ((a & 0x02) == 0x02) {
            list.add("ATTR_HIDDEN");
        }
        if ((a & 0x01) == 0x01) {
            list.add("ATTR_READ_ONLY");
        }
        return list;
    }

    /**
     * This method reads the contents of a file
     * 
     * @param file the file to read (if it exists)
     */
    public static void read(String file, String offset, String numBytes) {

        // get the file's size for validating
        long fileSize = getFileInfo(file, currentCluster, 0, true);
        if (fileSize == -1) {
            System.out.println("Error: " + file + " is not a file");
            return;
        }

        long off;
        long num;

        try {
            // turn the passed-in values into ints
            off = Integer.parseInt(offset);
            num = Integer.parseInt(numBytes);
        } catch (NumberFormatException e) {
            System.out.println("Error: OFFSET and NUM_BYTES must be numbers");
            return;
        }

        if (off < 0) { // offset is negative
            System.out.println("Error: OFFSET must be a positive value");
            return;
        }
        if (num <= 0) { // num is 0 or less
            System.out.println("Error: NUM_BYTES must be greater than zero");
            return;
        }
        if (num >= fileSize) { // offset + numBytes is greater than the file size
            System.out.println("Error: attempt to read data outside of file bounds");
            return;
        }

        // get the start cluster of the file
        int startClus = getFileInfo(file, currentCluster, 1, true);

        // get absolute location given start cluster
        long loc = absoluteLoc(startClus);

        try {

            // if the offset is greater than the cluster size, get the next cluster
            long copyofOff = off;

            while (copyofOff > (secPerClus * bytesPerSec)) {
                startClus = getNextCluster(startClus);
                if (startClus == -1) {
                    System.out.println("Error: attempt to read data outside of file bounds");
                    return;
                }
                loc = absoluteLoc(startClus);
                copyofOff -= (secPerClus * bytesPerSec);
            }

            // set the location to the start of requested point in the file
            raf.seek(loc + copyofOff);

            int bytesRead = 0;
            // read the bytes and print them
            while (bytesRead < num) {
                // if the location is at the end of the cluster, get the next cluster
                // if it is porperly reached the end of a thingy
                if ((bytesRead != 0) && (((bytesRead + copyofOff) % (secPerClus * bytesPerSec)) == 0)) {
                    startClus = getNextCluster(startClus);
                    if (startClus == -1) {
                        break;
                    }
                    loc = absoluteLoc(startClus);
                    raf.seek(loc);
                }

                // read the byte
                int b = raf.read();

                // if it's a valid character, print it
                if ((b >= 32 && b <= 126) || (b >= 7 && b <= 13)) {
                    System.out.print((char) b);
                }

                // else, print its hex value.
                else {
                    System.out.printf("0x%02X", b);
                }
                bytesRead++;
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("Error reading file");
            return;
        }
    }

    /**
     * prints the size of the file (if it exists)
     * 
     * @param file the file to find the size of
     */
    public static void size(String file) {
        // initilaize size and try to find it
        int size = 0;
        size = getFullFileInfo(file, 0);

        // if it actually is a file and exists
        if (size != -1) {
            System.out.println("Size of " + file + " is " + size + " bytes");
            return;
        }

        // if it's not a file then toss it.
        System.out.println("Error: " + file + " is not a file");
        return;
    }

    /**
     * This method gets all the files in a cluster and all the clusters that follow
     * and returns the desired information of the file
     * 
     * @param cluster the starting cluster
     * @param look    the type of data in question. 0 -> size of a file. 1 -> start
     *                cluster of a file. 2 -> start cluster of a dir, 3 -> the
     *                attributes tag
     * @return the list of all the files in the cluster and all the clusters that
     *         follow
     */
    private static int getFullFileInfo(String file, int look) {

        // check int the base cluster
        int fileSize = getFileInfo(file, currentCluster, look, true);

        // if it's in the base cluster, return it.
        if (fileSize != -1) {
            return fileSize;
        }

        // if there is more to go in the FAT, keep checking the next clusters for the
        // file
        // get value of currCluster that you can overwrite
        long current = currentCluster;
        while (true) {

            // get next cluster number (or -1 if it doesn't exist)
            long next = getNextCluster(current);
            if (next == -1) {
                break;
            }

            // check if the file is in the next cluster
            fileSize = getFileInfo(file, next, look, false);

            // if you found the file, return the size
            if (fileSize != -1) {
                return fileSize;
            }

            // update "current" value
            current = next;
        }

        return fileSize;
    }

    /**
     * This method finds the size of a file
     * 
     * @param file            the file to find the size of, from working directory
     * @param currentCluster2 the cluster to start looking in
     * @param look            an int to represent the things you're looking for. 0
     *                        is the
     *                        size and 1 is the start cluster of a file, 2 is the
     *                        start
     *                        cluster of a dir, 3 is the attributes tag
     * @return the size of the file, or -1 if it doesn't exist or isn't a file
     */
    private static int getFileInfo(String file, long currentCluster2, int look, boolean base) {

        // addition for if you're looking at the root cluster
        int addition = currentCluster2 == rootClus ? 0 : 32;

        // innerAdd for if you're looking at an inner cluster vs the base cluster for a
        // directory
        int innerAdd = base == true ? 64 : 0;

        // add em up and you get your absolute start position
        long start = absoluteLoc(currentCluster2) + innerAdd + addition;

        try {

            // remember the max amount i can be has to be the value of the clusters you have
            // minus how many you've already skipped.
            for (int i = 0; i < (secPerClus * bytesPerSec - (addition + innerAdd)); i = i + 64) {

                raf.seek(start);

                // set up the string builder and validity check
                StringBuilder sb = new StringBuilder();

                for (int j = 0; j < 11; j++) {
                    byte b = raf.readByte();
                    // if it's an empty file or deleted, break
                    if (b == (byte) 0x00 || b == (byte) 0xE5) {
                        while (j < 11) {
                            raf.readByte();
                            j++;
                        }
                    }

                    // if it's a space, it's a directory or a file but you've gotta skip files
                    else if (b == 0x20) {

                        // save where we were before
                        long prev = start + j + 1;

                        // if it's a dir then we're not printing ish after it
                        if (isDir(start)) {
                            while (j < 11) {
                                raf.readByte();
                                j++;
                            }
                        }

                        // otherwise add the period and skip the rest of the empty bytes
                        else {
                            raf.seek(prev);
                            sb.append(".");
                            while (j < 7) {
                                raf.readByte();
                                j++;
                            }
                        }
                    }

                    // if it's a char, add it to the string builder
                    else {
                        sb.append((char) b);
                    }
                }

                // if it's the file you're looking for, get the info
                String name = sb.toString();
                if (name.equals(file)) {

                    // method for finding the info. return if it's found and valid. otherwise,
                    // continue searching.
                    if (findFileData(start, look) != -1) {
                        return findFileData(start, look);
                    }

                }
                // increment and continue.
                start += 64;
            }
        } catch (Exception e) {
            System.out.println("Error reading file");
            return -2;
        }

        // you went through eveyrthing and couldn't find the file
        return -1;
    }

    /**
     * This method finds the data of a file specified by the val of look
     * 
     * @param start the start of the data to look through
     * @param look  the type of data in question. 0 -> size of a file. 1 -> start
     *              cluster of a file. 2 -> start cluster of a dir, 3 -> the
     *              attributes tag
     * @return
     */
    private static int findFileData(long start, int look) {

        try {
            // 0 means you're looking for a specific file. so make sure it's not a directory
            if (look == 0) {
                if (!isDir(start)) {
                    raf.seek(start + 28);
                    int size = raf.read() | raf.read() << 8 | raf.read() << 16 | raf.read() << 24;
                    return size;
                }
            }

            // one means you're looking for the start cluster of a FILE
            else if (look == 1) {
                if (!isDir(start)) {
                    raf.seek(start + 20);
                    int size = raf.read() << 16 | raf.read() << 24;
                    raf.seek(start + 26);
                    size = raf.read() | raf.read() << 8;
                    return size;
                }
            }

            // two means you're looking for the start cluster of a DIR
            else if (look == 2) {
                if (isDir(start)) {
                    raf.seek(start + 20);
                    raf.seek(start + 20);
                    int size = raf.read() << 16 | raf.read() << 24;
                    raf.seek(start + 26);
                    size = raf.read() | raf.read() << 8;
                    return size;
                }
            }

            // three means you're looking for the attributes tag
            else if (look == 3) {
                raf.seek(start + 11);
                int b = raf.readByte();
                return b;
            }

        } catch (Exception e) {
            System.out.println("Error reading file");
            return -2;
        }
        return -1;
    }

}