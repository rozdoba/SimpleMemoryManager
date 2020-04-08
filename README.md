# Simple Memory Manager

My java implementation of First Fit, Best Fit and Worst Fit memory allocation algorithms.

Compaction occurs when you have enough total memory to allocate a block, but memory allocation fails.

I use are a HashMaps for the process Memory Blocks, two TreeSets to sort the empty Memory Blocks, and the Memory Blocks
are nodes of a linked list. 

Program takes a file as the input (command line argument). The file is of this format:

The first line is a number between 1 and 3:
	
•	1 = First fit
•	2 = Best fit
•	3 = Worst fit

The second line of the input has the total size of the memory in KBs.

After the first two lines, each line is of one of these formats:
1.	A   PID   MEMORY_SIZE
2.	D   PID
3.	P

The first one is a request for process with id: PID to be allocated. Size of the memory required for this process is: MEMORY_SIZE. The unit is in KB.

The second one is deallocating memory for process with id PID.

The third one is asking the program to print out the current state of memory (allocations and free spaces). 

A sample input file:

2
16384
A   1   210  
A   2   1450  
A   3   8000
D   2
P
A   2   900
D   1
D   3
A   4   800
P
