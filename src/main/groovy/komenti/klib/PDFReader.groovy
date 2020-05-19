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

      (1..reader.getNumberOfPages()).each {
        stripper.setStartPage(it)
        stripper.setEndPage(it)

        def text = stripper.getText(reader).toLowerCase()

        text = text.replaceAll('\u2022', '.  ')
        text = text.replaceAll('â€“', '. ')
        text = text.replaceAll('\b-', '.  ')
        text = text.replaceAll('\b–', '. ')
        text = text.replaceAll('-\b', '.  ')
        text = text.replaceAll('–\b', '. ')
        text = text.replaceAll('\\s+', ' ')
        text = text.replaceAll(', \\?', '. ?')
        text = text.replaceAll('\\.', '. ')
        text = text.replaceAll('\n\n', '. ')

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
