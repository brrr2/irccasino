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
class HouseStat extends Stats {
    private ArrayList<PokerPlayer> donors;
    private ArrayList<PokerPlayer> winners;

    public HouseStat() {
        this(0);
    }

    public HouseStat(int pot) {
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
        String outStr = "";
        for (int ctr = 0; ctr < donors.size(); ctr++){
            outStr += donors.get(ctr).getNick() + " ";
        }
        return outStr.substring(0, outStr.length() - 1);
    }

    protected String getWinnersString(){
        String outStr = "";
        for (int ctr = 0; ctr < winners.size(); ctr++){
            outStr += winners.get(ctr).getNick() + " ";
        }
        return outStr.substring(0, outStr.length() - 1);
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
            for (int ctr = 0; ctr < size; ctr++){
                if (ctr == size-1){
                    if (winners.contains(donors.get(ctr))) {
                        outStr += donors.get(ctr).getNickStr();
                    } else {
                        outStr += donors.get(ctr).getNick();
                    }
                } else {
                    if (winners.contains(donors.get(ctr))) {
                        outStr += donors.get(ctr).getNickStr() + ", ";
                    } else {
                        outStr += donors.get(ctr).getNick() + ", ";
                    }
                }
            }   
        }
        return outStr;
    }

    @Override
    public String toString() {
        return "Biggest pot: $" + CardGame.formatNumber(get("biggestpot")) + " (" + getToStringList() + ").";
    }
}