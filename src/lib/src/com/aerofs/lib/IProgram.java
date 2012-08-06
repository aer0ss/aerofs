package com.aerofs.lib;

public interface IProgram
{
    public static class ExProgramNotFound extends Exception
    {
        private static final long serialVersionUID = 1L;

        public ExProgramNotFound(String prog)
        {
            super("Program not found: " + prog);
        }
    }

    /**
     * @param prog the name of the program to run
     * @param args the list of program arguments excluding the program name
     */
    void launch_(String rtRoot, String prog, String[] args) throws ExProgramNotFound, Exception;
}
