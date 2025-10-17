package gui.editor;

import automata.State;

import java.util.ArrayList;
import java.util.HashSet;

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
        private HashSet<State> statesAtThisCoordinate;

        public UniqueCoordinate(State state, int value) {
            this.value = value;
            this.numStatesAtThisCoordinate = 1;
            this.statesAtThisCoordinate = new HashSet<>();
            this.statesAtThisCoordinate.add(state);
        }

        public int getValue() {
            return value;
        }

        public void addState(State state) {
            this.numStatesAtThisCoordinate += 1;
            this.statesAtThisCoordinate.add(state);
        }

        public void removeState(State state) {
            this.numStatesAtThisCoordinate -= 1;
            this.statesAtThisCoordinate.remove(state);
        }

        public boolean hasStatesAtThisCoordinate() {
            return numStatesAtThisCoordinate > 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append(value);
            sb.append(":{");
            for (State state : this.statesAtThisCoordinate) {
                sb.append(state.toString());
                sb.append(",");
            }
            sb.append("}]");

            return sb.toString();
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

    protected IndexPair OLDgetClosestIndices(int value) {
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

    protected IndexPair getClosestIndices(State state, int value) {
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
                if (this.coordinates.get(middle).statesAtThisCoordinate.contains(state) && this.coordinates.get(middle).statesAtThisCoordinate.size() < 2) {
                    if (middle == 0 && this.coordinates.size() == 1) {
                        return null;
                    } else if (middle == 0) {
                        return new IndexPair(middle + 1, middle + 1);
                    } else if (middle == this.coordinates.size() - 1) {
                        return new IndexPair(middle - 1, middle - 1);
                    }
                    return new IndexPair(middle - 1, middle + 1);
                }
                return new IndexPair(middle, middle);
            }
        }

        return null;
    }

    public Integer getClosestValue(State state, int value) {
        IndexPair closest = getClosestIndices(state, value);
        if (closest == null) {
            return null;
        } else if (closest.first == closest.second) {
            return this.coordinates.get(closest.first).value;
        }
        else {
            int first = this.coordinates.get(closest.first).value;
            int second = this.coordinates.get(closest.second).value;
            int firstDiff = Math.abs(first - value);
            int secondDiff = Math.abs(second - value);

            if (firstDiff < secondDiff) {
                return first;
            } else if (firstDiff > secondDiff) {
                return second;
            } else {
                // Default to first - idk if this is the best
                return firstDiff;
            }
        }
    }

    public void addCoordinate(State state, int value) {
        IndexPair closest = getClosestIndices(state, value);
        if (closest == null) {
            // The list is empty, just add the value
            this.coordinates.add(new UniqueCoordinate(state, value));
        } else if (closest.first == closest.second) {
            // The value already exists in the list, increment the state count for that value
            this.coordinates.get(closest.first).addState(state);
        }
        else {
            // Insert the value between closest.first and closest.second so the list stays sorted
            this.coordinates.add(closest.second, new UniqueCoordinate(state, value));
        }
    }

    public void removeCoordinate(State state, int value) {
        IndexPair closest = getClosestIndices(state, value);
        if (closest == null) {
            // The list is empty, just return - This branch should never be reached
            return;
        } else if (closest.first == closest.second) {
            // The value already exists in the list, decrement the state count for that value
            this.coordinates.get(closest.first).removeState(state);
            if (!this.coordinates.get(closest.first).hasStatesAtThisCoordinate()) {
                // If no states have this coordinate, remove it from the list
                this.coordinates.remove(closest.first);
            }
        }
        else {
            // The list does not contain the value - This branch should never be reached
            return;
        }
    }

    public void updateCoordinate(State state, int oldValue, int newValue) {
        if (oldValue == newValue) {
            return;
        } else {
            this.removeCoordinate(state, oldValue);
            this.addCoordinate(state, newValue);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UniqueCoordinateHolder(");
        for (UniqueCoordinate coordinate : this.coordinates) {
            sb.append(coordinate.toString());
            sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }
}
