using System;
using System.Threading;

#pragma warning disable CA1303  // Localisation is not a goal at this time.


namespace Assignment2
{
    public static class Demo
    {
        public static void Main(string[] args)
        {
            Scenario inp = new Scenario();
            Console.WriteLine("width = " + inp.GetWidth() + ", height = " + inp.GetHeight());

            while(true)
            {
                // ... ?

                // For illustration purposes -- this just prints out the messages as they come in.
                Console.WriteLine("---");
                string? msg = inp.NextMessage();
                while(msg != null)
                {
                    Console.WriteLine(msg);
                    msg = inp.NextMessage();
                }

                // Wait 1 second
                Thread.Sleep(1000);
            }
        }
    }
}
