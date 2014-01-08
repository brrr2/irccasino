/*
    Copyright (C) 2013-2014 Yizhe Shen <brrr@live.ca>

    This file is part of irccasino.

    irccasino is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    irccasino is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with irccasino.  If not, see <http://www.gnu.org/licenses/>.
 */

package irccasino.cardgame;

import irccasino.Card;
import java.util.ArrayList;
import org.pircbotx.Colors;

/**
 * An object that represents a hand of cards.
 * @author Yizhe Shen
 */
public class Hand extends ArrayList<Card>{
    /**
     * Creates a Hand with an empty ArrayList of cards.
     */
    public Hand(){
        super();
    }

    /**
     * Returns the Card that matches the specified card.
     * @param c the card to match to
     * @return the matched card or null if not found
     */
    public Card get(Card c){
        int index = indexOf(c);
        if (index == -1) {
            return null;
        }
        return get(index);
    }

    /**
     * Default toString returns all cards in the hand face-up.
     * @return string representation of the cards in the hand all face-up
     */
    @Override
    public String toString(){
        return toString(0, size());
    }

    /**
     * Gets a string representation of the hand with hidden cards.
     * User can specify how many of the cards are hidden.
     * 
     * @param numHidden The number of hidden cards
     * @return a String with the first numHidden cards replaced
     */
    public String toString(int numHidden){
        if (size() == 0) {
            return "<empty>";
        }
        String hiddenBlock = Colors.DARK_BLUE+",00\uFFFD";
        String outStr = "";
        for (int ctr = 0; ctr < numHidden; ctr++){
            outStr += hiddenBlock + " ";
        }
        for (int ctr = numHidden; ctr < size(); ctr++){
            outStr += get(ctr) + " ";
        }
        return outStr.substring(0, outStr.length() - 1) + Colors.NORMAL;
    }

    /**
     * Gets an index-select string representation of the hand.
     * A space-delimited string of cards starting from start and excluding end.
     * 
     * @param start the start index.
     * @param end the end index.
     * @return a String showing the selected cards
     */
    public String toString(int start, int end){
        if (size() == 0) {
            return "<empty>";
        }
        String outStr = "";
        int slimit = Math.max(0, start);
        int elimit = Math.min(size(), end);
        for (int ctr = slimit; ctr < elimit; ctr++){
            outStr += get(ctr) + " ";
        }
        return outStr.substring(0, outStr.length() - 1) + Colors.NORMAL;
    }
}