package edu.curtin.app.message;


import static edu.curtin.app.message.Mistletoe.*;



/**
 * DO NOT ADD ANYTHING TO THIS FILE. This is just a utility to supply data to your Assignment 2
 * simulation app. Your code must work with the ORIGINAL version of this file.
 *
 * You must create a new Scenario object, call getWidth()/getHeight(), then call 'nextMessage()'
 * periodically, which returns the next message in a queue of randomly-generated messages.
 *
 * Note that some of the randomly-generated messages will be invalid!
 */
public class Scenario
{
    public int getWidth()
    {
        return (int)(long)MT(V("width")).get(0);
    }

    public int getHeight()
    {
        return (int)(long)MT(V("height")).get(0);
    }

    public String nextMessage()
    {
        var msg = MT(V("nextMessage").Call());
        return msg.isEmpty() ? null : (String)msg.get(0);
    }

    public void setErrorProbability(double errorProb)
    {
        MT(V("setErrorProbability").Call(errorProb));
    }

    {
        MT(
            V("screwUpP").Set(0.1),
            V("stormP").Set(0.2),
            V("cargoRunP").Set(0.3),

            V("cargoTypes").Set(
                "bananas", "mangoes", "wheat", "rye", "rice", "muffins", "pizza",
                "oil", "steel", "gold", "copper", "iron-ore", "timber", "wool", "cotton",
                "unobtainium", "sprockets", "widgets", "luxury-cars", "rubber-ducks",
                "striped-paint", "elbow-grease", "chlorine-trifluoride", "lock-picking-tools"),

            V("dimRange").Set(30, 50),
            V("nPortsRange").Set(5, 20),
            V("nShipsRange").Set(10, 20),
            V("cargoRange").Set(500, 5000),
            V("stormSizeRange").Set(2, 20),

            V("lastTime").Set(Time),
            V("errorProb").SetGlobal(V("screwUpP")),
            V("messages").Set(),

            V("setErrorProbability").SetGlobal(Fn(
                If(Arg.Lt(0).Or(Arg.Gt(0.5))).Then(
                    Throw("The error probability is limited to 0-0.5. (It is applied repeatedly, and hence values approaching 1 would create a real mess.)")
                ),
                V("errorProb").Update(Arg)
            )),

            V("randFloat").Set(Fn(
                Arg.As("min", "max"),
                Rand.Times(V("max").Minus(V("min"))).Plus(V("min"))
            )),

            V("randString").Set(Fn(
                Arg.As("len"),
                While(Idx.Lt(V("len"))).Do(
                    V("randInt").Call(0x20, 0x7e)
                )
                .Chr().Join()
            )),

            V("alpha").Set(Fn(
                V("n").Set(Arg.Plus(1)),
                V("s").Set(""),
                While(V("n").Gt(0)).Do(
                    V("s").Update("%(n.Minus(1).Mod(26).Plus(65).Chr())%(Val)"),
                    V("n").Update(V("n").Minus(1).Div(26))
                ),
                V("s")
            )),

            V("randInt").Set(Fn(V("randFloat").Call(Arg).Int())),

            V("width").SetGlobal(V("randInt").Call(V("dimRange"))),
            V("height").SetGlobal(V("randInt").Call(V("dimRange"))),

            V("nNorthPorts").Set(V("randInt").Call(V("nPortsRange"))),
            V("nEastPorts").Set(V("randInt").Call(V("nPortsRange"))),
            V("nSouthPorts").Set(V("randInt").Call(V("nPortsRange"))),
            V("nWestPorts").Set(V("randInt").Call(V("nPortsRange"))),
            V("nPorts").Set(V("nNorthPorts").Plus(V("nEastPorts")).Plus(V("nSouthPorts")).Plus(V("nWestPorts"))),

            V("messages").Append(
                C(
                    C(0).To(V("width"))
                        .RandChoose(V("nNorthPorts"))
                        .ForEach("port %(Idx.Apply(alpha)) %(Item) 0"),

                    C(1).To(V("height").Minus(1))
                        .RandChoose(V("nNorthPorts"))
                        .ForEach(
                            "port %(Idx.Plus(nNorthPorts).Apply(alpha)) %(width.Minus(1)) %(Item)"),

                    C(0).To(V("width"))
                        .RandChoose(V("nSouthPorts"))
                        .ForEach(
                            "port %(Idx.Plus(nNorthPorts).Plus(nEastPorts).Apply(alpha)) %(Item) %(height.Minus(1))"),

                    C(1).To(V("height").Minus(1))
                        .RandChoose(V("nWestPorts"))
                        .ForEach(
                            "port %(Idx.Plus(nNorthPorts).Plus(nEastPorts).Plus(nSouthPorts).Apply(alpha)) 0 %(Item)"),

                    C(0).To(V("randInt").Call(V("nShipsRange")))
                        .ForEach(
                            "ship %(Idx) %(randInt.Call(0, width)) %(randInt.Call(0, height))")
                ).RandShuffle()
            ),

            V("nextMessage").SetGlobal(Fn(
                V("thisTime").Set(Time),

                V("t").Set(V("lastTime").Plus(0.999)),
                While(V("t").Lt(V("thisTime"))).Do(
                    V("messages").Append(
                        C(
                            While(Rand.Lt(V("cargoRunP"))).Do(
                                V("from", "to").Set(C(0).To(V("nPorts")).RandChoose(2)),
                                "supply %(randInt.Call(cargoRange)) %(cargoTypes.RandChoose()) %(from.Apply(alpha)) %(to.Apply(alpha))"
                            ),

                            While(Rand.Lt(V("stormP"))).Do(
                                "storm %(randInt.Call(0, width)) %(randInt.Call(0, height)) %(randInt.Call(stormSizeRange))"
                            ),

                            While(Rand.Lt(V("errorProb"))).Do(
                                C(
                                    If(Rand.Lt(0.5)).Then(C("port", "ship", "supply", "storm").RandChoose()),
                                    While(Rand.Lt(0.75)).Do(
                                        If(Rand.Lt(0.33)).Then(
                                            V("randInt").Call(-99, 100)
                                        ).Else(
                                            If(Rand.Lt(0.5)).Then(
                                                V("randInt").Call(0, 52).Apply("alpha")
                                            ).Else(
                                                V("randString").Call(V("randInt").Call(1, 11))
                                            )
                                        )
                                    ),
                                    V("randString").Call(V("randInt").Call(1, 11))
                                ).Join(" ")
                            )
                        ).RandShuffle()
                    ),
                    V("t").Set(Val.Plus(1.0))
                ),
                V("lastTime").Update(V("thisTime")),
                V("messages").Pop()
            ))
        );
    }
}
