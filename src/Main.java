import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Comparator;

class MemoryManagerFactory {
    public SimpleMemoryManager getMemoryManager(int mode, int totalSize) {
        switch(mode) {
            case 1:
                return new FirstFitMemoryManager(totalSize);
            case 2:
                return new BestFitMemoryManager(totalSize);
            default:
                return new WorstFitMemoryManager(totalSize);
        }
    }
}

/**
 * Abstract class that will be extended by the
 * FirstFitMemoryManager, BestFitMemoryManager or WorstFitMemoryManager.
 */
abstract class SimpleMemoryManager {
    public int totalSize;
    public int occupiedSize;
    public MemoryBlock head;

    public Map<Integer, MemoryBlock> processBlocksMap;
    public TreeSet<MemoryBlock> limitSortedHoleBlocks;
    public TreeSet<MemoryBlock> baseSortedHoleBlocks;

    public SimpleMemoryManager(int totalSize) {
        //add the empty block to the head of the linked list
        MemoryBlock newBlock = new MemoryBlock(-1, totalSize);
        merge(null, newBlock);

        processBlocksMap = new HashMap<Integer, MemoryBlock>();
        limitSortedHoleBlocks = new TreeSet<MemoryBlock>(new SortByLimit());
        baseSortedHoleBlocks = new TreeSet<MemoryBlock>(new SortByBase());

        //add the empty block to all TreeSets
        addHoleBlock(newBlock);
        
        this.totalSize = totalSize;
        occupiedSize = 0;
    }
    
    /**
     * Fits an algorithm using FirstFit, BestFit or WorstFit depending on the instantiated class.
     */
    abstract boolean fitAlgorithm(MemoryBlock newBlock);

    /**
     * If there is enough space to allocate but allocation fails, 
     * compaction is performed and allocation will be reattempted.
     * @param mode
     * @param pid
     * @param limit
     */
    public void manageAllocation(int pid, int limit) {
        MemoryBlock newBlock = new MemoryBlock(pid, limit);
        if(newBlock.limit + occupiedSize <= totalSize) {
            if(!fitAlgorithm(newBlock)) {
                compaction();
                fitAlgorithm(newBlock);
            }
        } else {
            System.out.println("Not enough space to allocate pid: " + newBlock.pid);
        }
    }

    /**
     * Allocates a Process Block. Two general cases:
     * 1. Process Block is equal in size to the Hole Block and pid only needs to be changed.
     * 2. Process Block is smaller than the Hole Block, and is integrated with the Hole Block.
     */
    public void allocate(MemoryBlock newBlock, MemoryBlock holeBlock) {
        removeHoleBlock(holeBlock);
        if(newBlock.limit == holeBlock.limit) {
            holeBlock.pid = newBlock.pid;
            newBlock = holeBlock;
        } else {
            holeBlock = merge(holeBlock, newBlock);
            addHoleBlock(holeBlock);
        }

        // System.out.println("allocating: " + newBlock.pid + ": " + newBlock.limit + "\n");
        this.processBlocksMap.put(newBlock.pid, newBlock);
        this.occupiedSize += newBlock.limit;
    }

    /**
     * Called when a Process Block needs to be integrated with a Hole Block.
     * The newBlock (process) is inserted before the oldBlock (hole) and bases of
     * both blocks are updated.
     */
    public MemoryBlock merge(MemoryBlock oldBlock, MemoryBlock newBlock) {
        if(head == null) {
            head = newBlock;
            newBlock.base = 0;
            return oldBlock;
        }

        if(head == oldBlock) {
            head = newBlock;
            newBlock.next = oldBlock;
            oldBlock.prev = newBlock;

            oldBlock.limit = oldBlock.limit - newBlock.limit;
            oldBlock.base = oldBlock.prev.base + oldBlock.prev.limit;
            return oldBlock;
        }

        newBlock.next = oldBlock;
        newBlock.prev = oldBlock.prev;

        newBlock.base = newBlock.prev.base + newBlock.prev.limit;

        oldBlock.prev.next = newBlock;
        oldBlock.prev = newBlock;

        oldBlock.limit = oldBlock.limit - newBlock.limit;
        oldBlock.base = oldBlock.prev.base + oldBlock.prev.limit;
        return oldBlock;
    }

    /**
     * Removes a Process Block with matching pid from the HashMap. Checks to see 
     * if there are Hole Blocks above and below the deallocated block
     * (now a HoleBlock) and consolidates the Hole Blocks into a single larger Hole Block.
     */
    public void deallocate(int pid) {
        MemoryBlock block = this.processBlocksMap.remove(pid);
        if(block != null) {
            // System.out.println("deallocating: " + pid + ": " + block.limit + "\n");
            this.occupiedSize -= block.limit;
            block.pid = -1;

            int blockAboveLimit = 0;
            int blockBelowLimit = 0;
            
            if(block.next != null && block.next.pid == -1) {
                removeHoleBlock(block.next);
                blockAboveLimit = this.delete(block.next);
            }

            if(block.prev != null && block.prev.pid == -1) {
                removeHoleBlock(block.prev);
                blockBelowLimit = this.delete(block.prev);
            }
            
            block.limit += blockAboveLimit + blockBelowLimit;
            if(block.prev == null) {
                block.base = 0;
            } else {
                block.base = block.prev.base + block.prev.limit;
            }
            
            addHoleBlock(block);
        }
    }

    /**
     * Removes a Block from the Linked List, maintaining next and prev
     * references of the MemoryBlocks within the Linked List. Returns the
     * limit of the removed MemoryBlock.
     */
    public int delete(MemoryBlock block) {
        if(head == block) {
            head = block.next;
            head.prev = null;
            return block.limit;
        }
        
        if(block.next != null) {
            block.next.prev = block.prev;
        }
        
        block.prev.next = block.next;
        return block.limit;
    }

    /**
     * Creates a new HashMap of Process Blocks, and TreeSets of Hole Blocks. 
     * Process Blocks are compacted, and HoleBlocks are consolidated into a 
     * single large Hole Block that is added to the end of the Linked List.
     */
    public void compaction() {
        // System.out.println("compacting\n");
        clearMemoryBlocks();
        
        int pooledLimit = 0;
        MemoryBlock cur = this.head;
        head = null;
        
        while(cur != null) {
            if(cur.pid == -1) {
                pooledLimit += cur.limit;
            } else {
                MemoryBlock tailBlock = addToTail(cur.pid, cur.limit);
                processBlocksMap.put(tailBlock.pid, tailBlock);
            }
            cur = cur.next;
        }

        MemoryBlock pooledHoleBlock = addToTail(-1, pooledLimit);
        this.addHoleBlock(pooledHoleBlock);
    }

    /**
     * Adds a MemoryBlock to the end of the Linked List.
     */
    public MemoryBlock addToTail(int pid, int limit) {
        MemoryBlock newBlock = new MemoryBlock(pid, limit);
        if(head == null) {
            head = newBlock;
            newBlock.base = 0;
            return newBlock;
        }

        MemoryBlock curr = head;
        while(curr.next != null) {
            curr = curr.next;
        }

        curr.next = newBlock;
        newBlock.prev = curr;
        newBlock.base = newBlock.prev.base + newBlock.prev.limit;
        return newBlock;
    }

    /**
     * Prints the state of the MemoryManager by traversing through 
     * the LinkedList starting at head.
     */
    public String toString() {
        StringBuilder string = new StringBuilder();
        MemoryBlock cur = head;
        while(cur != null) {
            string.append(cur.toString());
            cur = cur.next;
        }
        return string.toString();
    }

    /**
     * Removes a holeBlock from both TreeSets.
     * @param holeBlock
     */
    public void removeHoleBlock(MemoryBlock holeBlock) {
        this.baseSortedHoleBlocks.remove(holeBlock);
        this.limitSortedHoleBlocks.remove(holeBlock);
    }

    /**
     * Adds a holeBlock to both TreeSets.
     * @param holeBlock
     */
    public void addHoleBlock(MemoryBlock holeBlock) {
        this.baseSortedHoleBlocks.add(holeBlock);
        this.limitSortedHoleBlocks.add(holeBlock);
    }

    /**
     * Clears all HashSets and TreeSets of their MemoryBlocks.
     */
    public void clearMemoryBlocks() {
        this.processBlocksMap.clear();
        this.baseSortedHoleBlocks.clear();
        this.limitSortedHoleBlocks.clear();
    }

}

/**
 * Synonymous to a Linked List Node class. Contains
 * base, limit, pid information, as well as references to the next and previous MemoryBlocks.
 */
class MemoryBlock {

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
 * Sorts MemoryBlocks by their limit in increasing order. If there is no difference
 * in limit, sorts MemoryBlocks by their base in increasing order.
 */
class SortByLimit implements Comparator<MemoryBlock> {
    public int compare(MemoryBlock block1, MemoryBlock block2) {
        int limitDifference = block1.limit - block2.limit;
        return (limitDifference == 0) ? block1.base - block2.base : limitDifference;
    }
}

/**
 * Sorts MemoryBlocks by their base in increasing order.
 */
class SortByBase implements Comparator<MemoryBlock> {
    public int compare(MemoryBlock block1, MemoryBlock block2) {
        return block1.base - block2.base;
    }
}

/**
 * Uses the FirstFit algorithm to allocate MemoryBlocks.
 */
class FirstFitMemoryManager extends SimpleMemoryManager {

    public FirstFitMemoryManager(int totalSize) {
        super(totalSize);
    }

    /**
     * A Process Block will be allocated into the first available Hole Block sorted 
     * by base.
     */
    public boolean fitAlgorithm(MemoryBlock newBlock) {
        Iterator<MemoryBlock> iterator = baseSortedHoleBlocks.iterator();
        while(iterator.hasNext()) {
            MemoryBlock holeBlock = iterator.next();
            if(newBlock.limit <= holeBlock.limit) {
                super.allocate(newBlock, holeBlock);
                return true;
            }
        }
        return false;
    }
}

/**
 * Uses the BestFit algorithm to allocate MemoryBlocks.
 */
class BestFitMemoryManager extends SimpleMemoryManager {

    public BestFitMemoryManager(int totalSize) {
        super(totalSize);
    }  

    /**
     * A Process Block will be allocated into the first available Hole Block sorted 
     * by limit.
     * @param pid
     * @param limit
     * @return
     */
    public boolean fitAlgorithm(MemoryBlock newBlock) {
        MemoryBlock holeBlock = limitSortedHoleBlocks.ceiling(newBlock);
        if(holeBlock != null && (newBlock.limit <= holeBlock.limit)) {
            super.allocate(newBlock, holeBlock);
            return true;
        } 
        return false;
    }
}

/**
 * Uses the WorstFit algorithm to allocate MemoryBlocks.
 */
class WorstFitMemoryManager extends SimpleMemoryManager {

    public WorstFitMemoryManager(int totalSize) {
        super(totalSize);
    } 

    /**
     * A Process Block will be allocated into the first available Hole Block sorted 
     * in reverse order by limit.
     * @param pid
     * @param limit
     * @return
     */
    public boolean fitAlgorithm(MemoryBlock newBlock) {
        MemoryBlock holeBlock = limitSortedHoleBlocks.last();
        if(holeBlock != null && (newBlock.limit <= holeBlock.limit)) {
            allocate(newBlock, holeBlock);
            return true;
        }
        return false;
    }
}

public class Main {

    /**
     * Main Driver of the Program.
     */
    public static void main(String[] args) {

        try {
            Scanner s = new Scanner(new File(args[0]));
            int mode = Integer.parseInt(s.nextLine());
            int totalMemorySize = Integer.parseInt(s.nextLine());
            MemoryManagerFactory mmFactory = new MemoryManagerFactory();
            SimpleMemoryManager smm = mmFactory.getMemoryManager(mode, totalMemorySize);

            String[] line = new String[3];
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
                    default:
                        pid = Integer.parseInt(line[1].trim());
                        limit = Integer.parseInt(line[2].trim());
                        smm.manageAllocation(pid, limit);
                }
            }
            s.close();
                
        } catch(FileNotFoundException ex) {
            System.out.println("Unable to find file.");
        }
    }
}