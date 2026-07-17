from scenario import Scenario
import time


def main():
    inp = Scenario()
    print(f"width = {inp.get_width()}, height = {inp.get_height()}")

    # We can't reproduce the Java call "System.in.available()", without much complication.
    # Instead, here's an infinite loop that ends on KeyboardInterrupt, an exception thrown
    # when the user presses Ctrl+C.
    try:
        while True:
            # ...

            # For illustration purposes -- this just prints out the messages as they come in.
            print('---')
            msg = inp.next_message()
            while msg is not None:
                print(msg)
                msg = inp.next_message()

            # Wait 1 second
            time.sleep(1)

    except KeyboardInterrupt:  # End program when the user presses Ctrl+C
        pass


if __name__ == '__main__':
    main()
