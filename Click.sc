AbstractClick {

	classvar <loopCues, <cueBufs;
	var <bpm, <beats, <beatDiv, <repeats, <>amp, <>out;
	var <tempoArray, <pattern;

	var <>clickFreq = 1000;

	// add clock stuff.here?! Something like:
	// clock = clock ? TempoClock.default;

	// the play / stop methods need to be revisited now that the classes are returning Pbinds/Pseqs
	// do the play/stop methods maybe return the EventStreamPlayer that gets created with each class? TBD.....

	*initClass {
		loopCues = IdentityDictionary();
		cueBufs  = IdentityDictionary();

		StartUp.add{

			ServerBoot.add({ |server|
				var pathToSounds = Click.filenameSymbol.asString.dirname +/+ "sounds/";

				PathName(pathToSounds).entries.do({ |entry|
					var cueName = entry.fileNameWithoutExtension.asSymbol;
					var buffer = Buffer.read(server,entry.fullPath);

					cueBufs.put(cueName,buffer);
				});
			},\default);

			SynthDef(\clickSynth,{
				var env = Env.perc(0.001, 0.25).kr(2);
				var sig = LFTri.ar(\freq.kr(1000));
				sig = sig * env * \amp.kr(0.25);
				OffsetOut.ar(\out.kr(0),sig);
			}).add;

			SynthDef(\clickCuePB,{
				var bufnum = \buf.kr();
				var sig = PlayBuf.ar(1,bufnum,BufRateScale.kr(bufnum),doneAction: 2);
				sig = sig * \amp.kr(0.25);
				OffsetOut.ar(\out.kr(0),sig);
			}).add;
		}
	}

	init {
		var barArray = this.makeBarArray;
		pattern      = this.prMakePattern(barArray);
	}

	makeBarArray {
		var barArray;

		if(beats.isInteger.not || beatDiv.isInteger.not,{
			beats = beats.floor;
			beatDiv = beatDiv.floor;
			"beats and beatDiv must be integers - they've been floored".warn;
		});

		if(beatDiv > 1,{
			var subDiv = Array.fill(beatDiv,{ 1 });
			subDiv[0] = 1.5;
			barArray = Array.fill(beats,subDiv);
			barArray = barArray.flat;
			barArray[0] = 2;
		},{
			if(beats == 1,{
				barArray = [2]
			},{
				barArray = Array.fill(beats,{ 1 });
				barArray[0] = 2;
			})
		});
		^barArray
	}

	makeCueBar { |clickBar|
		var cueBar;

		if(clickBar.size == 1,{
			cueBar = [\note]
		},{
			cueBar = clickBar.collect({ |item|
				if(item == 2,{ \note },{ \rest })
			})
		});
		^cueBar;
	}

	play { ^this.pattern.play }

	stop { ^this.pattern.stop }   // this doesn't work of course!!

	duration {
		var beatDur = 60 / bpm;
		^beatDur * beats * repeats;
	}
}

Click : AbstractClick {

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		var tempoArray = bpm.dup( beats * beatDiv ) * beatDiv;
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out, tempoArray).init;
	}

	prMakePattern { |barArray|
		var dur = 60 / (bpm * beatDiv);

		^Pbind(
			\instrument, \clickSynth,
			\type,\grain,
			\dur, dur,
			\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
			\amp, Pfunc({ amp.value }),             // allows me to pass both/either floats and {bus.getSynchronous}...a hack? Or exploiting polymorphism?!
			\out, Pfunc({ out }),                   // do I need the Pfunc here? Is this also for some bus trickery, or?
		)
	}
}

ClickCue : AbstractClick {

	var <>cueKey;

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, cueKey = 'bell', amp = 0.5, out = 0|
		var tempoArray = bpm.dup( beats * beatDiv ) * beatDiv;
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out, tempoArray)
		.cueKey_(cueKey.asSymbol).init;
	}

	prMakePattern { |barArray|
		var dur    = 60 / (bpm * beatDiv);
		var cueBar = this.makeCueBar(barArray);

		^Ppar([
			Pbind(
				\instrument, \clickSynth,
				\type,\grain,
				\dur, dur,
				\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			),

			Pbind(
				\instrument, \clickCuePB,
				\dur, dur,
				\type,Pseq( cueBar, repeats ),
				\buf, Pfunc({ cueBufs[cueKey] }),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			)
		])
	}
}

ClickEnv : AbstractClick {

	*new { |bpmStartEnd = #[60,120], beats = 1, beatDiv = 1, repeats = 1, curve = 0, amp = 0.5, out = 0|
		var strokes, bpms, tempoArray;
		if( bpmStartEnd.isArray.not or: { bpmStartEnd.size != 2 },{
			"bpmStartEnd must be an Array of 2 values".throw
		});
		strokes = beats * beatDiv;
		bpms    = bpmStartEnd * beatDiv;
		tempoArray = Array.fill(strokes,{ |i| i.lincurve(0, strokes - 1, bpms[0], bpms[1], curve) });
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs(bpmStartEnd, beats, beatDiv, repeats, amp, out, tempoArray).init;
	}

	prMakePattern { |barArray|

		^Pbind(
			\instrument, \clickSynth,
			\type,\grain,
			\dur, Pseq( 60 / tempoArray, repeats ),
			\freq,Pfunc({ clickFreq }) * Pseq( barArray, inf ),
			\amp, Pfunc({ amp.value }),
			\out, Pfunc({ out }),
		)
	}

	duration {
		var durs = (60 / this.tempoArray).sum;
		^durs * repeats;
	}

	plot { (this.tempoArray / this.beatDiv).plot }
}

ClickEnvCue : AbstractClick {

	var <>cueKey;

	*new { |bpmStartEnd = #[60,120], beats = 1, beatDiv = 1, repeats = 1, curve = 0, cueKey = 'bell', amp = 0.5, out = 0|
		var strokes, bpms, tempoArray;
		if( bpmStartEnd.isArray.not or: { bpmStartEnd.size != 2 },{
			"bpmStartEnd must be an Array of 2 values".throw
		});
		strokes = beats * beatDiv;
		bpms    = bpmStartEnd * beatDiv;
		tempoArray = Array.fill(strokes,{ |i| i.lincurve(0, strokes - 1, bpms[0], bpms[1], curve) });
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs(bpmStartEnd, beats, beatDiv, repeats, amp, out, tempoArray).cueKey_(cueKey.asSymbol).init;
	}

	prMakePattern { |barArray|
		var cueBar  = this.makeCueBar(barArray);

		^Ppar([
			Pbind(
				\instrument, \clickSynth,
				\type,\grain,
				\dur, Pseq( 60 / tempoArray, repeats ),
				\freq,Pfunc({ clickFreq }) * Pseq( barArray, inf ),
				\amp, Pfunc({ amp.value }),
				\out, Pfunc({ out }),
			),
			Pbind(
				\instrument, \clickCuePB,
				\dur, Pseq( 60 / tempoArray, repeats ),
				\type,Pseq( cueBar, repeats ),
				\buf, Pfunc({ cueBufs[cueKey] }),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			)
		])
	}

	duration {
		var durs = (60 / this.tempoArray).sum;
		^durs * repeats
	}

	plot { (this.tempoArray / this.beatDiv).plot }

}

ClickLoop : AbstractClick {

	var <>loopCue;

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, loopKey = nil, amp = 0.5, out = 0|
		var tempoArray = bpm.dup( beats * beatDiv ) * beatDiv;
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out, tempoArray).loopCue_(loopKey).init;
	}

	keyCheck {
		if(loopCue.isNil,{
			var key = UniqueID.next;
			"no loopKey assigned - using unique key: %".format( key ).warn;
			loopCue = key.asSymbol;
		});

		loopCues.put(loopCue, true);
	}

	prMakePattern { |barArray|
		var dur = 60 / (bpm * beatDiv);
		this.keyCheck;

		if(repeats != inf,{
			^Pbind(
				\instrument, \clickSynth,
				\dur, dur,
				\freq,Pwhile({ loopCues.at(loopCue) }, Pfunc({ clickFreq }) * Pseq( barArray, repeats )),
				\amp, Pfunc({ amp.value }),
				\out, Pfunc({ out }),
			)
		},{
			MethodError("ClickLoop must have finite length").throw
		});
	}
}

ClickMan : AbstractClick {

	*new { |bpmArray = #[60], beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		var tempoArray = bpmArray.dupEach(beatDiv) * beatDiv;
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs(bpmArray[0], bpmArray.size, beatDiv, repeats, amp, out, tempoArray).init;
	}

	prMakePattern { |barArray|
		var dur = 60 / (tempoArray.dupEach(beatDiv) * beatDiv);

		^Pbind(
			\instrument, \clickSynth,
			\type,\grain,
			\dur, Pseq( dur.flat, inf ),
			\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
			\amp, Pfunc({ amp.value }),
			\out, Pfunc({ out }),
		);
	}

	duration {
		var barDur = (60 / tempoArray).sum;
		^barDur * repeats;
	}
}

ClickManCue : AbstractClick {

	var <>cueKey;

	*new { |bpmArray = #[60], beatDiv = 1, repeats = 1, cueKey = 'bell', amp = 0.5, out = 0|
		var tempoArray = bpmArray.dupEach(beatDiv) * beatDiv;
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs(bpmArray[0], bpmArray.size, beatDiv, repeats, amp, out, tempoArray)
		.cueKey_(cueKey.asSymbol).init;
	}

	prMakePattern { |barArray|
		var dur    = 60 / (tempoArray.stutter(beatDiv) * beatDiv);
		var cueBar = this.makeCueBar(barArray);

		^Ppar([
			Pbind(
				\instrument, \clickSynth,
				\type,\grain,
				\dur, Pseq( dur.flat, inf ),
				\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			),

			Pbind(
				\instrument, \clickCuePB,
				\dur, Pseq( dur.flat, inf ),
				\type,Pseq( cueBar, repeats ),
				\buf, Pfunc({ cueBufs[cueKey] }),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			)
		]);
	}

	duration {
		var barDur = (60 / tempoArray).sum;
		^barDur * repeats;
	}
}

// made to receive PolyTempoComposer output (ie. arrays of onsets like: [ 0, 0.791, 1.469, 2.069, 2.615, 3.2 ] )
// beatDiv only marks accents for the click
ClickPTC : AbstractClick {

	*new { |onsetArray = #[0], beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		var beats = (onsetArray.size - 1) / beatDiv;
		var tempoArray = 60/onsetArray.differentiate[1..];
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs('ptc', beats.asInteger, beatDiv, repeats, amp, out, tempoArray).init
	}

	prMakePattern { |barArray|

		^Pbind(
			\instrument, \clickSynth,
			\dur, Pseq( 60 / tempoArray, inf ),
			\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
			\amp, Pfunc({ amp.value }),
			\out, Pfunc({ out }),
		);
	}

	duration { ^(60/tempoArray).sum }
}

ClickRest : AbstractClick {

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1|
		var tempoArray = bpm.dup( beats * beatDiv ) * beatDiv;
		tempoArray = tempoArray.dup( repeats ).flat;

		^super.newCopyArgs(bpm, beats, beatDiv, repeats, 0, 0,tempoArray).init;     // is there a better way to handle amp, out args?
	}

	init {
		pattern = this.prMakePattern;
	}

	prMakePattern {
		var dur = (60 / bpm) * beats;
		^Pbind( \dur, Pseq([ Rest(dur) ], repeats ) );
	}
}

/* classes for composing Clicks */

ClickConCat : AbstractClick {

	var <clickArray;

	*new { |reps ...clicks|
		^super.new.amp_(0.5).out_(0).init(reps, clicks);
	}

	init { |reps, clicks|
		repeats    = reps;
		clickArray = clicks.asArray.flat;
		pattern    = this.prMakePattern( reps );
	}

	prMakePattern { |repeats|
		var sumArray = clickArray.deepCollect(3,{ |clk| clk.pattern });
		^Pseq(sumArray,repeats);
	}

	bpm     { ^clickArray.first.bpm }
	beats   { ^clickArray.collect({ |clk| clk.beats }).dup( repeats ) }
	beatDiv { ^clickArray.collect({ |clk| clk.beatDiv }).dup( repeats ) }

	amp_ { |val|
		amp = val;
		clickArray.do({ |clk| clk.amp = amp });
	}

	out_ { |val|
		out = val;
		clickArray.do({ |clk| clk.out = out });
	}

	duration {
		var durs = clickArray.collect({ |clk| clk.duration });
		^durs.sum * repeats;
	}

	tempoArray {
		var tempi = clickArray.collect({ |clk| clk.tempoArray }).flat;
		^ tempi.dup( repeats ).flat
	}

	exportMIDItempoMap { |path, verbose = false|
		var tempi = this.tempoArray;
		var times = (60 / tempi).integrate;
		var file = SimpleMIDIFile( path.asString ).init0( tempi.first );
		file.timeMode = \seconds;
		times = times.rotate;
		times[0] = 0;

		times.do({ |time, i|
			if(verbose,{ [tempi[i], time].postln });
			file.addTempo(tempi[i],time)
		});
		file.write;
	}
}

ClickConCatLoop : AbstractClick {

	var <clickArray, <>loopCue;

	*new { |loopKey ...clicks|
		^super.new.loopCue_(loopKey).amp_(0.5).out_(0).init(clicks);
	}

	init { |clicks|
		clickArray = clicks.asArray.flat;
		pattern    = this.prMakePattern;
	}

	prMakePattern { |key, loopKey|
		var sumArray = clickArray.deepCollect(3,{ |clk| clk.pattern });
		this.keyCheck;
		^Pwhile({ loopCues.at(loopCue) }, Pseq(sumArray) );
	}

	keyCheck {
		if(loopCue.isNil,{
			var key = UniqueID.next;
			"no loopKey assigned - using unique key: %".format( key ).warn;
			loopCue = key.asSymbol;
		});

		loopCues.put(loopCue, true);
	}

	bpm     { ^clickArray.first.bpm }
	beats   { ^clickArray.collect({ |clk| clk.beats }).dup( repeats ) }
	beatDiv { ^clickArray.collect({ |clk| clk.beatDiv }).dup( repeats ) }

	repeats { "This is a looping class, calling this method is probably a mistake".error }

	amp_ { |val|
		amp = val;
		clickArray.do({ |clk| clk.amp = amp });
	}

	out_ { |val|
		out = val;
		clickArray.do({ |clk| clk.out = out });
	}

	duration {
		var durs = clickArray.collect({ |clk| clk.duration });
		^durs.sum
	}
}