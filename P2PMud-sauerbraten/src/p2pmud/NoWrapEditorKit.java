package p2pmud;

import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.BlockView;
import javax.swing.text.html.HTMLEditorKit;

public class NoWrapEditorKit extends HTMLEditorKit {
	public ViewFactory getViewFactory() {
		final ViewFactory vf = super.getViewFactory();

		return new ViewFactory() {
			public View create(Element elem) {
				View ret = vf.create(elem);

				if (ret.getClass() == BlockView.class) {
				    return new BlockView(elem, ((BlockView)ret).getAxis()) {
				    	public void layout(int width, int height) {
				    		super.layout(32768, height);
				    	}
				    	public float getMinimumSpan(int axis) {
				    		return super.getPreferredSpan(axis);
				    	}
				    };
				} else {
					return ret;
				}
			}
		};
	}
}
