import java.awt.Dimension
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.border.LineBorder
import javax.swing.SpringLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints
import javax.swing.BoxLayout
import java.awt.BorderLayout
import static java.awt.BorderLayout.*
import static java.awt.GridBagConstraints.*
import java.awt.*
import javax.swing.*
import javax.swing.border.*
import groovy.swing.SwingBuilder
import net.miginfocom.swing.MigLayout

if (args.length != 1) {
	println "Usage: Test port"
	System.exit(1);
}

output = null
pendingCommands = [:]
count = 0
commands = [
	periodic: {args ->
		println "PERIODIC COMMAND"
		addCmd('periodic', "sleep 1000 [remotesend periodic]")
	}
]
def addCmd(key, value) {
	synchronized (pendingCommands) {
		pendingCommands[key] = value
	}
}

def runCommand(str) {
	def args = str.split()
	def func = commands[args[0]]

	if (func) {
		func(args.length > 1 ? args[1..-1] : [])
	}
}

def start() {
	sock = new ServerSocket(Integer.parseInt(args[0]))
	println "READY"
	while (true) {
		Socket client = sock.accept({
			println("Got connection...")
			output = it.getOutputStream()
			it.getInputStream().eachLine {
//				println "RECEIVED: $it"
				runCommand(it)
				dumpCommands()
			}
			try {it.shutdownInput()} catch (Exception ex) {}
			try {it.shutdownOutput()} catch (Exception ex) {}
			println "Disconnect"
		});
	}
}

def dumpCommands() {
	if (output) {
		synchronized (pendingCommands) {
			output << pendingCommands.collect{it.value}.join(";") + '\n'
			println "SENT: ${pendingCommands.collect{it.value}.join(';') + '\n'}"
			pendingCommands = [:]
		}
		output.flush()
	}
}

def send(label, field) {
	if (field.text && field.text[0]) {
		def cmd = "ent.$label p0 ${field.text}"
		println "NEW $label: $cmd"
		addCmd(label, cmd)
		dumpCommands()
	}
}
def newX() {
	send('x', xField)
}
def newY() {
	send('y', yField)
}
def newZ() {
	send('z', zField)
}
def cmd() {
	if (cmdField.text && cmdField.text[0]) {
		addCmd('cmd', cmdField.text)
		cmdField.text = ""
		dumpCommands()
	}
}
swing = new SwingBuilder()
swing.build {
	f = frame(title: 'Position', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
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
commands.position = {args ->
	swing.edt {
		xField.text = args[0]
		yField.text = args[1]
		zField.text = args[2]
	}
}

new Thread({start()}).start()
