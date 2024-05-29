# Fat32 img reader in Java

This was a school project made to navigate through a FAT32 file system.  
It effectively is a small shell that is able to read a FAT32 .img file.  
The shell is able to understand list directory content, navigate through the file system, and read file content.  

---

### To compile and run, input the following:

```bash
./compandrun.sh
```

---

### Once running, you will have access to the following commands 
  - `stop`: This command is used to terminate the program.
  - `info`: This command returns the information about the current directory, irrespective of the directory from which it is called.
  - `ls`: This command mimics the Linux "ls -a" command. It lists all items in the current directory, including "..", ".", and hidden folders. The items are displayed in alphabetical order.
  - `stat` {file/dir}: This command prints the size, attributes, and next cluster for a specified file or directory in the current working directory. If no attribute is found, it prints 'none'. If the specified file or directory does not exist, it prints an error.
  - `size` {file}: This command prints the size of a specified file in bytes. If the file does not exist or if a directory is specified, it prints an error.
  - `cd` {dir}: This command changes the current directory to the specified directory and updates the current working directory. If the specified argument is not a directory, it prints an error.
  - `read` {file} {offset} {bytes}: This command reads a specified file from a given offset to the sum of the offset and the specified bytes. It prints the text in ASCII format. If it is unable to do so, it prints "0xNN". If the file is not accessible or if the parameters are invalid, it prints an error.

---

### Scripts:

There are included scripts for mounting, unmounting, running, and testing the the fat32 reader.
The following are the files in the order from above:
- `mntfat32.sh`
- `unmount.sh`
- `run.sh`
- `test.sh`
