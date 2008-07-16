import javax.swing.JFrameimport net.miginfocom.swing.MigLayout
import groovy.swing.SwingBuilder
import java.awt.Dimension

swing = new SwingBuilder()
swing.build {
	f = frame(title: 'Position', defaultCloseOperation: JFrame.EXIT_ON_CLOSE, layout: new MigLayout('fillx'), pack: true, show: true) {
		label(name: 'xLabel', text: "x: ")
		xField = textField(name: 'xField', actionPerformed: {newX()}, focusLost: {newX()}, constraints: 'wrap, growx')
		label(name: 'yLabel', text: "y: ")
		yField = textField(name: 'yField', actionPerformed: {newY()}, focusLost: {newY()}, constraints: 'wrap, growx')
		label(name: 'zLabel', text: "z: ")
		zField = textField(name: 'zField', actionPerformed: {newZ()}, focusLost: {newZ()}, constraints: 'wrap, growx')
		label(name: 'cmdLabel', text: "Command: ")
		cmdField = textField(name: 'cmdField', actionPerformed: {cmd()}, focusLost: {cmd()}, constraints: 'wrap, growx')
	}
	f.size = [500, (int)f.size.height] as Dimension
}
