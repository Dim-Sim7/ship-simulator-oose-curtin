#include "scenario.h"

#include <chrono>
#include <iostream>
#include <memory>
#include <thread> // Only to use 'sleep_for()'. We're not doing multithreading.


int main(void)
{
    Scenario inp;
    std::shared_ptr<std::string> msg;
    std::cout << "width = " << inp.get_width() << + ", height = " << inp.get_height() << "\n";
    while(true)
    {
        // ...

        // For illustration purposes -- this just prints out the messages as they come in.
        std::cout << "---\n" << std::flush;
        msg = inp.next_message();
        while(msg != nullptr)
        {
            std::cout << *msg << "\n" << std::flush;
            msg = inp.next_message();
        }

        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    return 0;
}
