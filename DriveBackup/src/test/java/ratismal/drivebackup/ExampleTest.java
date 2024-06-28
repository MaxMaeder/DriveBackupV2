package ratismal.drivebackup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExampleTest {

    @Test
    void exampleWithMockito() {
        @SuppressWarnings("unchecked")
        List<String> mockedList = mock();

        when(mockedList.get(0)).thenReturn("first");

        assertEquals("first", mockedList.get(0));

        assertNull(mockedList.get(999));
    }
}