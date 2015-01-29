TARGET = patent
SRC = patent.tex \
      introduction.tex \
      aliasing.tex \
      alias_states.tex \
      alias_algo.tex \
      expulsion.tex \
      conflicts.tex \
      migration.tex \
      team_server.tex \
      vers_history.tex \
      sync_status.tex \
      collector.tex
BIB = $(TARGET).bib
FIGS = $(wildcard figs/*.pdf)
CONFIG = myxhtml.cfg
default: pdf

pdf: $(SRC) $(BIB) $(FIGS)
	pdflatex $(TARGET)
	-bibtex $(TARGET)
	pdflatex $(TARGET)
	pdflatex $(TARGET)

html: $(SRC) $(BIB) $(FIGS) $(CONFIG)
	htlatex $(TARGET) $(CONFIG)
	-bibtex $(TARGET)
	htlatex $(TARGET) $(CONFIG)
	htlatex $(TARGET) $(CONFIG)

word: $(SRC) $(BIB) $(FIGS)
	pandoc -t docx -o $(TARGET).docx $(TARGET).tex

clean:
	rm -rf *.log
	ls $(TARGET)* | grep -v $(TARGET).tex | grep -v $(BIB) | grep -v $(TARGET).docx | xargs rm
