package com.goodloop.chat.web;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.winterwell.utils.FailureException;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.Printer;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.app.Logins;

import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Vector.Norm;

public class HuggingFaceAPIClientTest {

	@Test
	public void testSmokeTest() {
		LoginDetails ld = Logins.get("huggingface");
		assert ld.apiKey != null;
		HuggingFaceAPIClient hf = new HuggingFaceAPIClient(ld.apiKey);
		//https://huggingface.co/osanseviero/full-sentence-distillroberta3		
//		hf.setRepo("osanseviero").setModelName("full-sentence-distillroberta3"); 404?
		//https://www.sbert.net/docs/pretrained_models.html#sentence-embedding-models
		hf.setRepo("sentence-transformers").setModelName("paraphrase-MiniLM-L6-v2");
		hf.setWait(new Dt(30, TUnit.SECOND));
//		setRepo("sentence-transformers").setModelName("paraphrase-MiniLM-L6-v2");
		
		Map inputs = hf.inputsForSentenceSimilarity("I want to make an advert", Arrays.asList(
				"Please help me make an advert",
				"I want to make an advert",
				"I want to get out of here",
				"Please give me some milk",
				"I am a cat"));
		Object out = hf.run(inputs);
		Printer.out(out);
		
//		Map<String,DenseVector> embeddings = new ArrayMap(); 
//		for(String s : ,
//		}) {
//			Object out = hf.run(s);
//			List embedding = Containers.asList(out);
//			double[] vec = MathUtils.toArray(embedding);
//			DenseVector dv = new DenseVector(vec);
//			embeddings.put(s, dv);
//		}
//		for(String a : embeddings.keySet()) {
//			for(String b : embeddings.keySet()) {
//				DenseVector ea = embeddings.get(a);
//				DenseVector eb = embeddings.get(b);
//				double ab = ea.norm(Norm.Two) * eb.norm(Norm.Two);
//				double aDotB = ea.dot(eb);
//				double cosm = aDotB / ab;
//				Printer.out(a,"	< "+Printer.prettyNumber(cosm, 3)+" >	",b);
//			}
//		}
	}

}
