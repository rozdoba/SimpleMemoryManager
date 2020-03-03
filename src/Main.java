import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;

public class Main {

    /**
     * Synonymous to a Linked List Node class. Contains information such as
     * base, limit, pid, as well as references to the next and previous MemoryBlock.
     */
    public static class MemoryBlock {
    
        public int pid;
        public int base;
        public int limit;
        MemoryBlock next;
        MemoryBlock prev;

        public MemoryBlock(int pid, int limit) {
            this.pid = pid;
            this.limit = limit;
        }

        public String toString() {
            return String.format("Pid: %d | Base: %d | Limit: %d\n", this.pid, this.base, this.limit);
        }
    }

    /**
     * Sorts MemoryBlocks by their limit in increasing order.
     */
    public static class SortByLimit implements Comparator<MemoryBlock> {
        public int compare(MemoryBlock block1, MemoryBlock block2) {
            return block1.limit - block2.limit;
        }
    }

    /**
     * Sorts MemoryBlocks by their base in increasing order.
     */
    public static class SortByBase implements Comparator<MemoryBlock> {
        public int compare(MemoryBlock block1, MemoryBlock block2) {
            return block1.base - block2.base;
        }
    }

    /**
     * Simple Memory Manager uses HashMap manage the allocation and deallocation of 
     * 'Process Blocks'. An ArrayList is used to manage 'Hole Blocks'. The order of
     * Hole Blocks and Process Blocks are maintained by using a doubly 
     * LinkedList data structure, where the head is the first MemoryBlock to be allocated.
     */
    public static class SimpleMemoryManager {
    
        public int totalSize;
        public int occupiedSize;
        public MemoryBlock head;
    
        public HashMap<Integer, MemoryBlock> processBlocksMap = new HashMap<Integer, MemoryBlock>();
        public ArrayList<MemoryBlock> holeBlocksList = new ArrayList<MemoryBlock>();
    
        public SimpleMemoryManager(int totalSize) {
            MemoryBlock newBlock = new MemoryBlock(-1, totalSize);
            this.merge(null, newBlock);
            this.holeBlocksList.add(newBlock);
            this.totalSize = totalSize;
            this.occupiedSize = 0;
        }

        /**
         * Runs an allocation algorithm based on the mode argument. If allocation fails,
         * compaction is performed and allocation will be reattempted.
         * @param mode
         * @param pid
         * @param limit
         */
        public void manageAllocation(int mode, int pid, int limit) {
            if(!chooseAlgorithm(mode, pid, limit)) {
                this.compaction();
                if(!chooseAlgorithm(mode, pid, limit)) {
                    System.out.println("Not enough space to allocate pid: " + pid);
                }
            }
        }

        /**
         * Chooses the correct allocation algorithm depending on the mode.
         * mode 1 = first fit, mode 2 = best fit, mode 3 = worst fit.
         * @param mode
         * @param pid
         * @param limit
         * @return
         */
        private boolean chooseAlgorithm(int mode, int pid, int limit) {
            boolean allocated = false;
            switch(mode) {
                case 1:
                    allocated = this.firstFit(pid, limit);
                    break;
                case 2:
                    allocated = this.bestFit(pid, limit);
                    break;
                case 3:
                    allocated = this.worstFit(pid, limit);
                    break;
            }
            return allocated;
        }

        /**
         * A Process Block will be allocated into the first available Hole Block sorted 
         * by base.
         */
        private boolean firstFit(int pid, int limit) {
            Collections.sort(this.holeBlocksList, new SortByBase());
            return allocate(pid, limit);
        }

        /**
         * A Process Block will be allocated into the first available Hole Block sorted 
         * by limit.
         * @param pid
         * @param limit
         * @return
         */
        private boolean bestFit(int pid, int limit) {
            Collections.sort(this.holeBlocksList, new SortByLimit());
            return allocate(pid, limit);
        }

        /**
         * A Process Block will be allocated into the first available Hole Block sorted 
         * in reverse order by limit.
         * @param pid
         * @param limit
         * @return
         */
        private boolean worstFit(int pid, int limit) {
            Collections.sort(this.holeBlocksList, new SortByLimit().reversed());
            return allocate(pid, limit);
        }

        /**
         * Returns true if the new Process Block has been allocated, false otherwise.
         * Allocates a Process Block if there is enough space to do so. Two cases:
         * Process Block is equal in size to the Hole Block and pid only needs to be changed.
         * Process Block is smaller than the Hole Block, and is integrated with the Hole Block.
         */
        private boolean allocate(int pid, int limit) {
            MemoryBlock holeBlock;
            MemoryBlock newBlock;
            boolean allocated = false;
    
            if(limit + occupiedSize <= totalSize) {
                for(int i = 0; i < this.holeBlocksList.size(); i++) {
                    holeBlock = this.holeBlocksList.get(i);

                    if(limit <= holeBlock.limit) {
                        if(limit == holeBlock.limit) {
                            newBlock = this.holeBlocksList.remove(i);
                            newBlock.pid = pid;
                        } else {
                            newBlock = new MemoryBlock(pid, limit);
                            merge(holeBlock, newBlock);
                        }

                        // System.out.println("allocating: " + pid + ": " + newBlock.limit + "\n");
                        this.processBlocksMap.put(pid, newBlock);
                        this.occupiedSize += limit;
                        allocated = true;
                        break;
                    }
                }
            }
            
            return allocated;
        }

        /**
         * Called when a Process Block needs to be integrated with a Hole Block.
         * The newBlock (process) is inserted before the oldBlock (hole) and bases of
         * both blocks are updated.
         */
        private void merge(MemoryBlock oldBlock, MemoryBlock newBlock) {
            if(this.head == null) {
                this.head = newBlock;
                newBlock.base = 0;
                return;
            }

            if(this.head == oldBlock) {
                this.head = newBlock;
                newBlock.next = oldBlock;
                oldBlock.prev = newBlock;

                oldBlock.limit = oldBlock.limit - newBlock.limit;
                oldBlock.base = oldBlock.prev.base + oldBlock.prev.limit;
                return;
            }

            newBlock.next = oldBlock;
            newBlock.prev = oldBlock.prev;

            newBlock.base = newBlock.prev.base + newBlock.prev.limit;

            oldBlock.prev.next = newBlock;
            oldBlock.prev = newBlock;

            oldBlock.limit = oldBlock.limit - newBlock.limit;
            oldBlock.base = oldBlock.prev.base + oldBlock.prev.limit;
        }

        /**
         * Removes a Process Block with matching pid from the HashMap. Checks to see 
         * if there are Hole Blocks above and below the deallocated block
         * (now a HoleBlock) and consolidates the Hole Blocks into a single larger Hole Block.
         */
        private void deallocate(int pid) {
            MemoryBlock block = this.processBlocksMap.remove(pid);
            if(block != null) {
                // System.out.println("deallocating: " + pid + ": " + block.limit + "\n");
                this.occupiedSize -= block.limit;
                block.pid = -1;

                int blockAboveLimit = 0;
                int blockBelowLimit = 0;
                
                if(block.next != null && block.next.pid == -1) {
                    this.holeBlocksList.remove(block.next);
                    blockAboveLimit = this.delete(block.next);
                }

                if(block.prev != null && block.prev.pid == -1) {
                    this.holeBlocksList.remove(block.prev);
                    blockBelowLimit = this.delete(block.prev);
                }
                
                block.limit += blockAboveLimit + blockBelowLimit;
                if(block.prev == null) {
                    block.base = 0;
                } else {
                    block.base = block.prev.base + block.prev.limit;
                }
                
                this.holeBlocksList.add(block);
            }
        }

        /**
         * Removes a Block from the Linked List, maintaining next and prev
         * references of the MemoryBlocks within the Linked List. Returns the
         * limit of the removed MemoryBlock.
         */
        private int delete(MemoryBlock block) {
            if(this.head == block) {
                this.head = block.next;
                this.head.prev = null;
                return block.limit;
            }
            
            if(block.next != null) {
                block.next.prev = block.prev;
            }
            
            block.prev.next = block.next;
            return block.limit;
        }

        /**
         * Creates a new HashMap of Process Blocks, ArrayList of HoleBlocks and
         * Linked List of Memory Blocks. Process Blocks are compacted, and HoleBlocks
         * are consolidated into a single large Hole Block that is added to the end of
         * the Linked List.
         */
        private void compaction() {
            System.out.println("compacting\n");
            this.processBlocksMap.clear();
            this.holeBlocksList.clear();
            
            int pooledLimit = 0;
            MemoryBlock cur = this.head;
            this.head = null;
            
            while(cur != null) {
                if(cur.pid == -1) {
                    pooledLimit += cur.limit;
                } else {
                    MemoryBlock tailBlock = this.addToTail(cur.pid, cur.limit);
                    this.processBlocksMap.put(tailBlock.pid, tailBlock);
                }
                cur = cur.next;
            }

            MemoryBlock pooledHoleBlock = this.addToTail(-1, pooledLimit);
            this.holeBlocksList.add(pooledHoleBlock);
        }

        /**
         * Adds a MemoryBlock to the end of the Linked List.
         */
        private MemoryBlock addToTail(int pid, int limit) {
            MemoryBlock newBlock = new MemoryBlock(pid, limit);
            if(this.head == null) {
                this.head = newBlock;
                newBlock.base = 0;
                return newBlock;
            }

            MemoryBlock curr = this.head;
            while(curr.next != null) {
                curr = curr.next;
            }

            curr.next = newBlock;
            newBlock.prev = curr;
            newBlock.base = newBlock.prev.base + newBlock.prev.limit;
            return newBlock;
        }

        public String toString() {
            StringBuilder string = new StringBuilder();
            MemoryBlock cur = this.head;
            while(cur != null) {
                string.append(cur.toString());
                cur = cur.next;
            }
            return string.toString();
        }
    }

    /**
     * Main Driver of the Program.
     */
    public static void main(String[] args) {

        try {
            Scanner s = new Scanner(new File(args[0]));
            int mode = Integer.parseInt(s.nextLine());
            int totalMemorySize = Integer.parseInt(s.nextLine());
            SimpleMemoryManager smm = new SimpleMemoryManager(totalMemorySize);

            String line[];
            int pid;
            int limit;

            while (s.hasNext()) {
                line = s.nextLine().trim().split("\\s+");
                switch(line.length) {
                    case 1:
                        System.out.println(smm.toString());
                        break;
                    case 2:
                        pid = Integer.parseInt(line[1]);
                        smm.deallocate(pid);
                        break;
                    case 3:
                        pid = Integer.parseInt(line[1].trim());
                        limit = Integer.parseInt(line[2].trim());
                        smm.manageAllocation(mode, pid, limit);
                        break;
                }
            }
            s.close();
                
        } catch(FileNotFoundException ex) {
            System.out.println("Unable to find file.");
        }
    }
}