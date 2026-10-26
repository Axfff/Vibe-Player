class DiscoveryError(Exception):
    """Only controlled codes cross the API/log boundary, never upstream error bodies."""

    def __init__(self, code: str, status: int = 502):
        self.code = code
        self.status = status
        super().__init__(code)
