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

package irccasino.texaspoker;

import irccasino.Stats;
import irccasino.cardgame.CardGame;
import java.util.ArrayList;

/**
 * Stores game statistics for TexasPoker. 
 * @author Yizhe Shen
 */
public class HouseStat extends Stats {
    private ArrayList<PokerPlayer> donors;
    private ArrayList<PokerPlayer> winners;

    public HouseStat() {
        this(0);
    }

    public HouseStat(int pot) {
        super();
        set("biggestpot", pot);
        donors = new ArrayList<PokerPlayer>();
        winners = new ArrayList<PokerPlayer>();
    }

    protected int getNumDonors() {
        return donors.size();
    }
    protected void addDonor(PokerPlayer p){
        donors.add(p);
    }
    protected void clearDonors(){
        donors.clear();
    }
    protected int getNumWinners() {
        return winners.size();
    }
    protected void addWinner(PokerPlayer p){
        winners.add(p);
    }
    protected void clearWinners(){
        winners.clear();
    }

    protected String getDonorsString(){
        if (donors.isEmpty()) {
            return "";
        } else {
            String outStr = "";
            for (PokerPlayer donor : donors) {
                outStr += donor.getNick() + " ";
            }
            return outStr.substring(0, outStr.length() - 1);
        }
    }

    protected String getWinnersString(){
        if (winners.isEmpty()) {
            return "";
        } else {
            String outStr = "";
            for (PokerPlayer winner : winners) {
                outStr += winner.getNick() + " ";
            }
            return outStr.substring(0, outStr.length() - 1);
        }
    }

    protected String getToStringList(){
        String outStr;
        int size = donors.size();
        if (size == 0){
            outStr = CardGame.formatBold("0") + " players";
        } else if (size == 1){
            outStr = CardGame.formatBold("1") + " player: " + donors.get(0).getNickStr();
        } else {
            outStr = CardGame.formatBold(size) + " players: ";
            for (PokerPlayer donor : donors) {
                if (winners.contains(donor)) {
                    outStr += donor.getNickStr(false) + ", ";
                } else {
                    outStr += donor.getNick(false) + ", ";
                }
            }
            outStr = outStr.substring(0, outStr.length() - 2);
        }
        return outStr;
    }

    @Override
    public String toString() {
        return "Biggest pot: $" + CardGame.formatNumber(get("biggestpot")) + " (" + getToStringList() + ").";
    }
}