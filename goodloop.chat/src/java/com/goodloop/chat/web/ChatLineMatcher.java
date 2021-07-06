package com.goodloop.chat.web;

import java.util.List;
import java.util.Map;

import com.winterwell.maths.stats.distributions.discrete.IFiniteDistribution;
import com.winterwell.maths.stats.distributions.discrete.ObjectDistribution;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Cache;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.app.Logins;

import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Vector.Norm;
/**
 * 
 * TODO use https://www.sbert.net/docs/pretrained_models.html
 * locally
 * 
 * @testedby {@link ChatLineMatcherTest}
 * @author daniel
 *
 */
public class ChatLineMatcher {

	Cache<String,DenseVector> embeddings = new Cache(1000);
	private HuggingFaceAPIClient hf;
	
	public ChatLineMatcher() {
		LoginDetails ld = Logins.get("huggingface");
		hf = new HuggingFaceAPIClient(ld.apiKey);
//		hf.setRepo("osanseviero").setModelName("full-sentence-distillroberta3"); 404?!
		hf.setRepo("sentence-transformers").setModelName("paraphrase-MiniLM-L6-v2");
		hf.setWait(new Dt(30, TUnit.SECOND));
	}
	
	public IFiniteDistribution<String> match(String input, List<String> options) {
		Map inp = hf.inputsForSentenceSimilarity(input, options);
		Object out = hf.run(inp);
		List<Number> vec = Containers.asList(out);
		ObjectDistribution od = new ObjectDistribution<>();
		for (int i=0; i<options.size(); i++) {
			double vi = vec.get(i).doubleValue();
			if (vi <= 0) {
				Log.d("no chance", input+" <> "+options.get(i));
				continue;
			}
			od.setProb(options.get(i), vi);
		}
		if (true) return od;
		
		
		// no embeddings :(
		ObjectDistribution<String> objdist = new ObjectDistribution<>();
		DenseVector eInput = embedding(input);
		List<DenseVector> eOptions = Containers.apply(options, o -> embedding(o));
		for(int i=0; i<options.size(); i++) {
			DenseVector eo = eOptions.get(i);
			double ab = eInput.norm(Norm.Two) * eo.norm(Norm.Two);
			double aDotB = eInput.dot(eo);
			double cosm = aDotB / ab;
			Printer.out(input,"	< "+Printer.prettyNumber(cosm, 3)+" >	", options.get(i));
			objdist.setProb(input, cosm); // HACK interpret similarity as a probability
		}
		objdist.normalise();
		return objdist;
	}
//	setRepo("sentence-transformers").setModelName("paraphrase-MiniLM-L6-v2");

	private DenseVector embedding(String input) {
		String cinput = StrUtils.toCanonical(input);
		DenseVector dv = embeddings.get(cinput);
		if (dv != null) {
			return dv;
		}
		List embedding = Containers.asList(hf.run(input));
		double[] vec = MathUtils.toArray(embedding);
		dv = new DenseVector(vec);
		embeddings.put(cinput, dv);
		return dv;
	}

}
