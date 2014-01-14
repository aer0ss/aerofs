ROOT_PATH = $$PWD/..

TEMPLATE = app
CONFIG -= qt                # Do not use Qt
CONFIG -= app_bundle        # Do not create an app bundle on OS X

INCLUDEPATH += \
    $$ROOT_PATH \
    $$ROOT_PATH/3rd_party

LIBS += -lprotobuf -lprotoc

# Preprocess *.tpl files in the TEMPLATES variable to generate the .tpl.h headers
gen_tpl.input = TEMPLATES
gen_tpl.output  = ${QMAKE_FILE_NAME}.h
gen_tpl.commands = $$ROOT_PATH/tools/gen_template ${QMAKE_FILE_NAME}
gen_tpl.variable_out = HEADERS
QMAKE_EXTRA_COMPILERS += gen_tpl
