package p2pmud


import rice.Continuation
import rice.Continuation.MultiContinuation


public class Tools extends BasicTools {

	def static continuation(cont, parent = null) {
		def validParent = parent && !(cont instanceof Continuation)

		[
		 	receiveResult: guard({r-> (validParent && !cont.receiveResult ? parent : cont).receiveResult(r)}),
			receiveException: guard({e -> (validParent && !cont.receiveException ? parent : cont).receiveException(e)})
		] as Continuation
	}
	def static serialContinuations(cont, items, block) {
		serialContinuation(cont, items, block, 0, [])
	}
	def static serialContinuation(cont, items, block, pos, results) {
		if (pos < items.size()) {
			block(items[pos], continuation(receiveResult: {r ->
				results.add(r)
				serialContinuation(cont, items, block, pos + 1, results)
			}, receiveException: {e ->
				cont.receiveException(new ContinuationException(e, results))
			}))
		} else {
			cont.receiveResult(results)
		}
	}
	def static parallelContinuations(cont, items, block) {
		def multi = new MultiContinuation(continuation(cont, receiveResult: {res ->
			def errorCount = 0

			res.each {
				if (it instanceof Exception) {
					errorCount++
				}
			}
			if (error) {
				cont.receiveException(new ContinuationException("There were " + errorCount + " errors in this parallel continuation"), res)
			} else {
				cont.receiveResult(res)
			}
		}), items.size())
		def counter = 0

		items.each {
			block(it, multi.getSubContinuation(counter++))
		}
	}
}
