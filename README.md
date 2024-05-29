# Fat32 Image Reader in Java

This was a school project to read Fat32 .img files
It also offers a range of commands and data requests through a CLI

---

### To compile and run, input the following:

```bash
./compandrun.sh
```


### Once running, you will have access to the following commands 
  - `stop`: Terminate the program.
  - `info`: Returns the information about the file system building blocks (starting cluster, cluster size, FAT size, etc.)
  - `ls`: This command mimics the Linux "ls -a" command. It lists (alphabetically, by SHORT name) all items in the current directory including ".", "..", as well as any hidden folders.
  - `stat` [file / dir]: This command prints the size, attributes, and next cluster for a specified file or directory in the current working directory. If no attribute is found, it prints 'none'. If the specified file or directory does not exist, it prints an error.
  - `size` [file]: This command prints the size of a specified file in bytes. If the file does not exist or if a directory is specified, it prints an error.
  - `cd` [dir]: This command changes the current directory to the one specified and updates the current working directory. If the specified argument is not a directory or doesn't exist, it prints an error.
  - `read` [file] [offset] [bytes]: This command reads [bytes] bytes from the [file] starting at [offset]. It prints each character in ASCII format, or "0xNN" if it is not possible. If the file is not accessible or if the parameters are invalid, it prints an error.

---

### Testing:

There is an included script for testing and printing a series of commands into `output.txt`.
To do so, run the following command:

```bash
./test.sh
```

