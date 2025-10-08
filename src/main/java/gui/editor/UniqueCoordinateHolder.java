package gui.editor;

import java.util.ArrayList;

/**
 * @author Jesse Burdick-Pless
 */
public class UniqueCoordinateHolder {

    /**
     * @author Jesse Burdick-Pless
     */
    protected class UniqueCoordinate {
        private final int value;
        private int numStatesAtThisCoordinate;

        public UniqueCoordinate(int value, int numStatesAtThisCoordinate) {
            this.value = value;
            this.numStatesAtThisCoordinate = numStatesAtThisCoordinate;
        }

        public UniqueCoordinate(int value) {
            this.value = value;
            this.numStatesAtThisCoordinate = 1;
        }

        public int getValue() {
            return value;
        }

        public void addState() {
            this.numStatesAtThisCoordinate += 1;
        }

        public void removeState() {
            this.numStatesAtThisCoordinate -= 1;
        }

        public boolean hasStatesAtThisCoordinate() {
            return numStatesAtThisCoordinate > 0;
        }
    }

    protected class IndexPair {
        public int first;
        public int second;
        public IndexPair(int first, int second) {
            this.first = first;
            this.second = second;
        }
    }

    private ArrayList<UniqueCoordinate> coordinates = new ArrayList<>();

    public UniqueCoordinateHolder() {
        this.coordinates = new  ArrayList<>();
    }

    protected IndexPair getClosestIndices(int value) {
       int start = 0;
       int end = this.coordinates.size() - 1;

       while (start <= end) {
           int middle = ((end - start) / 2) + start;

           if (value < this.coordinates.get(middle).value) {
               end = middle - 1;
               if (start == end) {
                   return new IndexPair(start, end + 1);
               }
           } else if (value > this.coordinates.get(middle).value) {
               start = middle + 1;
               if (start == end) {
                   return new IndexPair(start - 1, end);
               }
           } else {
               return new IndexPair(middle, middle);
           }
       }

       return null;
    }

    public void addCoordinate(int value) {
        IndexPair closest = getClosestIndices(value);
        if (closest == null) {
            // The list is empty, just add the value
            this.coordinates.add(new UniqueCoordinate(value));
        } else if (closest.first == closest.second) {
            // The value already exists in the list, increment the state count for that value
            this.coordinates.get(closest.first).addState();
        }
        else {
            // Insert the value between closest.first and closest.second so the list stays sorted
            this.coordinates.add(closest.second, new UniqueCoordinate(value));
        }
    }

    public void removeCoordinate(int value) {
        IndexPair closest = getClosestIndices(value);
        if (closest == null) {
            // The list is empty, just return - This branch should never be reached
            return;
        } else if (closest.first == closest.second) {
            // The value already exists in the list, decrement the state count for that value
            this.coordinates.get(closest.first).removeState();
            if (!this.coordinates.get(closest.first).hasStatesAtThisCoordinate()) {
                // If no states have this coordinate, remove the it from the list
                this.coordinates.remove(closest.first);
            }
        }
        else {
            // The list does not contain the value - This branch should never be reached
            return;
        }
    }

    public void updateCoordinate(int oldValue, int newValue) {
        if (oldValue == newValue) {
            return;
        } else {
            this.removeCoordinate(oldValue);
            this.addCoordinate(newValue);
        }
    }
}
