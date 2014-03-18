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

import java.util.TimerTask;

/**
 * Start round task to be performed after post-start waiting period.
 * @author Yizhe Shen
 */
public class StartRoundTask extends TimerTask{
    private final CardGame game;
    
    public StartRoundTask(CardGame g){
        game = g;
    }

    @Override
    public void run(){
        game.startRound();
    }
}