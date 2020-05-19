package klib

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

public class PDFReader {
  def pages = []

  def PDFReader(file) {
    def reader
    try {
      reader = PDDocument.load(file)
      def stripper = new PDFTextStripper()
      stripper.setAddMoreFormatting(true)

      (1..reader.getNumberOfPages()).each {
        stripper.setStartPage(it)
        stripper.setEndPage(it)

        def text = stripper.getText(reader).toLowerCase()

        text = text.replaceAll('\n\n', '. ')
        text = text.replaceAll('\u2022', '.  ')
        text = text.replaceAll('â€“', '. ')
        text = text.replaceAll('\\s-', '.  ')
        text = text.replaceAll('–\\s', '. ')
        text = text.replaceAll('\\s-', '.  ')
        text = text.replaceAll('–\\s', '. ')
        text = text.replaceAll('\\s+', ' ')
        text = text.replaceAll(', \\?', '. ?')
        text = text.replaceAll('\\.', '. ')

        // this sucks
        text = text.replaceAll('m edications', 'medication')
        text = text.replaceAll('a llergies', 'allergies')
        text = text.replaceAll('p ast', 'past')

        pages << text
      }

      reader.close()
    } catch(e) {
      println "Failed to load document!"
      e.printStackTrace() 
    } 
  } 

  Iterator iterator() {
    return pages.iterator()
  }

  def getText() {
    this.collect().join('\n')
  }
}
