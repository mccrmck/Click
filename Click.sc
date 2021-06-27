Click {

	classvar <all;
	var <bpm, <beats, <beatDiv, <loop, <pan, <amp, <out;
	var <key, <pattern;

	*initClass {

		all = IdentityDictionary.new;

		StartUp.add{

			SynthDef(\clickSynth,{
				var env = Env.perc(\atk.kr(0.01),\rls.kr(0.25),1.0,\curve.kr(-4)).kr(2);
				var sig = LFTri.ar(\freq.kr(1000));                                        // or SinOsc.ar()?
				sig = LPF.ar(sig,8000);                                                    // consider RLPF w/ low rq?
				sig = Pan2.ar(sig * env,\pan.kr(0),\amp.kr(0.5));
				OffsetOut.ar(\outBus.kr(0),sig);
			}).add;
		}
	}

	*new { |bpm = 60, beats = 1, beatDiv = 1, loop = true, pan = 0, amp = 0.5, out = 0|    // does it work to pass a float OR a busNumber for amp and out??
		^super.newCopyArgs(bpm, beats, beatDiv, loop, pan, amp, out).init;
	}

	init {
		var key = this.prGenerateKey;
		var barArray = this.prCreateBarArray;
		var pattern = this.prMakePattern(barArray,key);

		all.put(key,pattern);

	}

	prGenerateKey {
		var subDiv = switch(beatDiv,
			1,"q", // quarter
			2,"e", // eighth
			3,"t", // triplet
			4,"s", // sixteenth
			5,"f", // quintuplet (fives)
			{"Mike's laziness doesn't support more than quintuplet subdivisions".postln}
		);

		key = "t%_%%".format(bpm,beats,subDiv).asSymbol;

		^key
	}

	prCreateBarArray {
		var barArray;

		if(beatDiv > 1,{
			var subDiv = Array.fill(beatDiv,{1});
			subDiv[0] = 1.5;
			barArray = Array.fill(beats,subDiv);
			barArray = barArray.flat;
			barArray[0] = 2;
		},{
			if(beats == 1,{
				barArray = [1]
			},{
				barArray = Array.fill(beats,{1});
				barArray[0] = 2;
			})
		});

		^barArray
	}

	prMakePattern { |bar, key|
		var dur = 60 / (this.bpm * beatDiv); // this must account for beatDiv and beats!!

		switch(this.loop,
			true,{
				pattern = Pdef(key.asSymbol,
					Pbind(
						\instrument, \clickSynth,
						\dur, Pseq([dur],inf),
						\freq, Pseq(1000 * bar,inf),
						\amp, Pfunc({0.5}), // can this be an internal bus? dvs. can it be a getter and setter so that it can be updated while running?
						\outBus, Pfunc({0}),   // must be integer bus number!!!
					)
				);
			},

			false,{},

			{"loop arg must be boolean".throw}
		)

		^pattern
	}

	play { this.pattern.play}

	stop {
		this.pattern.clear;
		all.removeAt(key)
	}

}



/*
ClickLoop : Click {

-can I somehow integrate the Pwhile??
-needs to receive the cue as argument
-can the cue be reset this way as well?


- maybe I can inherit the whole Click class and just overwrite prMakePattern ???

loop {|true|} //setting to false causes the cue to happen

}


ClickEnv : Click {

// can pass in args to a Pseg? gotta figure this one out...

}

ClickMan : Click {

// manual input of barArray and somehow durArray!! This is for asymmetric Clicks (the 11/8 riff, for example)

}

ClickCue : Click {


- maybe I can inherit the whole Click class and just overwrite prMakePattern ???


-plays the bell instead of the Click...or both already in a Ppar?
-plays back both Click pattern and Bell pattern in a Ppar?

-must check -> some of the clicks have a long count in, some are shorter...make two separate methods? Boolean? two different Classes?
-or just make this have more flexible arguments -> pass array for bell rhythms?

}


*/



