player-ladder-om
=================
[![Build Status](https://travis-ci.org/minimal/player-ladder.svg?branch=master)](https://travis-ci.org/minimal/player-ladder)
[![Dependencies Status](http://jarkeeper.com/minimal/player-ladder/status.svg)](http://jarkeeper.com/minimal/player-ladder)

A player ladder for any 1v1 game (e.g. table tennis).

Originally forked from [React Tutorial Om](https://github.com/jalehman/react-tutorial-om)

Frontend written in [Om](https://github.com/swannodette/om).

## Installation

Clone this repo

    git clone git@github.com:minimal/react-tutorial-om.git

Run server and figwheel

    lein repl
    (go)
    (start-figwheel)
    
Browser repl

    lein repl :connect
    (browser-repl)

Point Browser to

    http://localhost:3000/app
    
Uberjar

    lein uberjar
    java -jar react-tutorial-om-0.1.0-SNAPSHOT-standalone.jar results.edn
